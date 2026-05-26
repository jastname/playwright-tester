package io.github.jastname.playwrighttester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 방식으로 패스워드 필드를 암호화/복호화합니다.
 * <p>
 * 저장 형식: {@code ENC(base64(iv[12] + ciphertext + authTag[16]))}
 * <ul>
 *   <li>키는 {@code app.crypto.secret-key} (Base64 인코딩된 32바이트) 또는 기본값 사용</li>
 *   <li>운영 환경에서는 환경변수 {@code APP_CRYPTO_SECRET_KEY} 로 오버라이드 권장</li>
 * </ul>
 */
@Service
public class PasswordCryptoService {

    private static final Logger log = LoggerFactory.getLogger(PasswordCryptoService.class);

    static final String PREFIX = "ENC(";
    static final String SUFFIX = ")";

    private static final String ALGORITHM   = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH   = 12;   
    private static final int    TAG_BITS    = 128;  


    private static final byte[] FALLBACK_KEY =
            "playwright-tester-default-32byte".getBytes(StandardCharsets.UTF_8);

    private static final ObjectMapper OM = new ObjectMapper();

    private final SecretKey secretKey;

    public PasswordCryptoService(
            @Value("${app.crypto.secret-key:}") String configuredKey) {

        byte[] keyBytes;
        if (configuredKey == null || configuredKey.isBlank()) {
            keyBytes = FALLBACK_KEY;
        } else {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(configuredKey.trim());
            } catch (IllegalArgumentException e) {
                log.error("[PasswordCrypto] 키 Base64 디코딩 실패, 기본 키 사용: {}", e.getMessage());
                decoded = FALLBACK_KEY;
            }
            if (decoded.length != 32) {
                log.error("[PasswordCrypto] 키 길이 오류 ({}바이트). AES-256은 32바이트 필요. 기본 키 사용.",
                        decoded.length);
                keyBytes = FALLBACK_KEY;
            } else {
                keyBytes = decoded;
            }
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("[PasswordCrypto] AES-256-GCM 초기화 완료");
    }

    // ── 핵심 암호화/복호화 ────────────────────────────────────────────────

    /**
     * 평문을 암호화합니다.
     * 이미 {@code ENC(...)} 형식이면 그대로 반환합니다.
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return plainText;
        if (isEncrypted(plainText)) return plainText;

        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV(12) + ciphertext+tag 연결 후 Base64
            byte[] combined = new byte[IV_LENGTH + cipherBytes.length];
            System.arraycopy(iv,         0, combined, 0,         IV_LENGTH);
            System.arraycopy(cipherBytes, 0, combined, IV_LENGTH, cipherBytes.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined) + SUFFIX;
        } catch (Exception e) {
            log.error("[PasswordCrypto] 암호화 실패: {}", e.getMessage());
            return plainText; // 실패 시 평문 그대로 반환
        }
    }

    /**
     * {@code ENC(...)} 형식의 암호문을 복호화합니다.
     * 암호화되지 않은 값이면 그대로 반환합니다.
     */
    public String decrypt(String encryptedText) {
        if (!isEncrypted(encryptedText)) return encryptedText;

        try {
            String b64 = encryptedText.substring(PREFIX.length(),
                    encryptedText.length() - SUFFIX.length());
            byte[] combined = Base64.getDecoder().decode(b64);

            byte[] iv         = new byte[IV_LENGTH];
            byte[] cipherBytes = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0,         iv,         0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherBytes);

            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[PasswordCrypto] 복호화 실패: {}", e.getMessage());
            return encryptedText; // 실패 시 암호문 그대로 반환
        }
    }

    /** {@code ENC(...)} 형식인지 확인합니다. */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    // ── JSON 일괄 처리 ────────────────────────────────────────────────────

    /**
     * 시나리오 배열 JSON 문자열에서 password 타입 스텝의 fillText 를 암호화합니다.
     * 저장 전에 호출합니다.
     *
     * @param json  시나리오 배열 [{...steps:[...]}, ...]
     * @return      fillText 가 암호화된 JSON 문자열
     */
    public String encryptPasswordsInJson(String json) {
        if (json == null || json.isBlank()) return json;
        try {
            JsonNode root = OM.readTree(json);
            if (!root.isArray()) return json;

            ArrayNode scenarios = (ArrayNode) root;
            for (JsonNode scenario : scenarios) {
                JsonNode steps = scenario.path("steps");
                if (!steps.isArray()) continue;
                for (JsonNode step : steps) {
                    processStepForEncrypt((ObjectNode) step);
                }
            }
            return OM.writeValueAsString(scenarios);
        } catch (Exception e) {
            log.error("[PasswordCrypto] JSON 암호화 처리 실패: {}", e.getMessage());
            return json;
        }
    }

    /**
     * 단일 시나리오 JSON 문자열에서 password 타입 스텝의 fillText 를 암호화합니다.
     * 개별 시나리오 수정(PUT) 시 호출합니다.
     */
    public String encryptPasswordsInScenarioJson(String json) {
        if (json == null || json.isBlank()) return json;
        try {
            JsonNode root = OM.readTree(json);
            JsonNode steps = root.path("steps");
            if (!steps.isArray()) return json;

            for (JsonNode step : steps) {
                processStepForEncrypt((ObjectNode) step);
            }
            return OM.writeValueAsString(root);
        } catch (Exception e) {
            log.error("[PasswordCrypto] 시나리오 JSON 암호화 처리 실패: {}", e.getMessage());
            return json;
        }
    }

    /**
     * step ObjectNode 에서 password 필드 여부를 판단합니다.
     * <ol>
     *   <li>{@code inputType} 필드가 "password"</li>
     *   <li>{@code type} 필드가 "password" (구버전 데이터 호환)</li>
     *   <li>{@code idName} 또는 {@code selector} 에 "pass" / "pwd" 포함 (하위 호환 휴리스틱)</li>
     * </ol>
     */
    private boolean isPasswordField(ObjectNode step) {
        // 1. inputType 필드
        String inputType = step.path("inputType").asText("");
        if ("password".equalsIgnoreCase(inputType)) return true;

        // 2. type 필드 (구버전)
        String type = step.path("type").asText("");
        if ("password".equalsIgnoreCase(type)) return true;

        // 3. 휴리스틱: idName / selector 에 "pass" 또는 "pwd" 포함
        String idName   = step.path("idName").asText("").toLowerCase();
        String selector = step.path("selector").asText("").toLowerCase();
        return idName.contains("pass") || idName.contains("pwd")
            || selector.contains("pass") || selector.contains("pwd");
    }

    /**
     * step ObjectNode 에서 inputType=password 이고 fillText 가 있으면 암호화합니다.
     */
    private void processStepForEncrypt(ObjectNode step) {
        if (!isPasswordField(step)) return;

        JsonNode fillTextNode = step.get("fillText");
        if (fillTextNode == null || fillTextNode.isNull()) return;

        String fillText = fillTextNode.asText();
        if (fillText.isBlank() || isEncrypted(fillText)) return;

        step.put("fillText", encrypt(fillText));
        log.debug("[PasswordCrypto] fillText 암호화 완료 | selector={}",
                step.path("selector").asText("?"));
    }
}
