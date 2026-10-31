package com.jiugeng.erp.iam;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/settings/user-defaults")
@PreAuthorize("hasAuthority('system.users.manage')")
class UserDefaultsController {
    private final JdbcClient jdbc; private final RoleRepository roles; private final AuditService audit; private final String key;
    UserDefaultsController(JdbcClient jdbc, RoleRepository roles, AuditService audit,
            @Value("${user_defaults_key:${ERP_USER_DEFAULTS_KEY:}}") String key) {
        this.jdbc=jdbc; this.roles=roles; this.audit=audit; this.key=key;
    }
    record Defaults(String initialPassword) {}
    @GetMapping ResponseEntity<Defaults> get() {
        String encrypted=jdbc.sql("SELECT encrypted_password FROM iam_user_defaults WHERE id=1").query(String.class).optional().orElse("");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Defaults(encrypted.isEmpty()?"":crypt(encrypted,false)));
    }
    @PutMapping @Transactional ResponseEntity<Defaults> save(@RequestBody Defaults input, @AuthenticationPrincipal IamTypes.SessionPrincipal actor) {
        roles.lock(); roles.requireAdministrator(actor.userId());
        String password=input.initialPassword();
        if(password==null || (!password.isEmpty() && (password.length()<6 || password.length()>128 || !password.matches("(?s).*[A-Za-z].*") || !password.matches("(?s).*\\d.*"))))
            throw new IamException(400,"PASSWORD_POLICY_VIOLATION","密码须为6至128位，并同时包含字母和数字；留空可取消默认值");
        jdbc.sql("UPDATE iam_user_defaults SET encrypted_password=:value,updated_at=CURRENT_TIMESTAMP WHERE id=1")
            .param("value",password.isEmpty()?"":crypt(password,true)).update();
        audit.append(AuditTypes.Actor.authenticated(actor),"CONFIG","USER_DEFAULT_PASSWORD_UPDATE","SECURITY_SETTING","user_defaults",AuditTypes.Outcome.SUCCESS,null,Map.of("configured",!password.isEmpty()),null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Defaults(password));
    }
    private String crypt(String value,boolean encrypt) {
        try {
            byte[] secret=Base64.getDecoder().decode(key);
            if(secret.length!=32) throw new IllegalStateException();
            byte[] iv=new byte[12]; byte[] bytes;
            if(encrypt) { new SecureRandom().nextBytes(iv); bytes=value.getBytes(StandardCharsets.UTF_8); }
            else { byte[] packed=Base64.getDecoder().decode(value); System.arraycopy(packed,0,iv,0,12); bytes=java.util.Arrays.copyOfRange(packed,12,packed.length); }
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(secret,"AES"),new GCMParameterSpec(128,iv));
            cipher.updateAAD("iam-user-default-password-v1".getBytes(StandardCharsets.UTF_8));
            byte[] output=cipher.doFinal(bytes);
            if(!encrypt) return new String(output,StandardCharsets.UTF_8);
            byte[] packed=new byte[iv.length+output.length]; System.arraycopy(iv,0,packed,0,iv.length); System.arraycopy(output,0,packed,iv.length,output.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch(Exception e) { throw new IamException(503,"USER_DEFAULTS_UNAVAILABLE","默认密码配置暂不可用，请检查服务加密配置"); }
    }
}
