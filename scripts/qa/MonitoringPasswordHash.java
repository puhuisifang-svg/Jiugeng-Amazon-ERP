import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Test-only helper. Receives an ephemeral password over stdin; never logs plaintext. */
class MonitoringPasswordHash {
    public static void main(String[] args) throws Exception {
        var password = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        System.out.print(new BCryptPasswordEncoder().encode(password));
    }
}
