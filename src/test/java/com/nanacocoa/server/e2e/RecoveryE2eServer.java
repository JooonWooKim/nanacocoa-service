package com.nanacocoa.server.e2e;

import com.nanacocoa.server.NanacocoaServerApplication;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.recovery.mail.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 테스트 classpath 전용. 운영 jar에 수신함·고정 계정·테스트 API를 포함하지 않는다. */
public class RecoveryE2eServer {
    public static void main(String[] args) {
        String mysql=System.getenv("RECOVERY_E2E_DB_URL");
        if(mysql!=null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) throw new IllegalArgumentException("Loopback test database required");
        new SpringApplicationBuilder(NanacocoaServerApplication.class, Fixture.class).profiles("test").run(
            "--spring.config.name=application-recovery-e2e", "--server.address=127.0.0.1", "--server.port=18086",
            "--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2",
            "--spring.datasource.url="+(mysql==null?"jdbc:h2:mem:recovery_e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1":mysql),
            "--spring.datasource.driver-class-name="+(mysql==null?"org.h2.Driver":"com.mysql.cj.jdbc.Driver"),
            "--spring.datasource.username="+(mysql==null?"sa":"root"), "--spring.datasource.password=",
            "--spring.flyway.enabled=true", "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
            "--spring.sql.init.mode=never", "--toss-payments.enabled=false", "--product-image.storage-mode=mock",
            "--payment.reconciliation.enabled=false", "--order.inventory.expiration-enabled=false",
            "--recovery.enabled=true", "--recovery.mode=mock", "--recovery.base-url=http://127.0.0.1:18086",
            "--recovery.allow-local-http=true", "--logging.level.root=WARN");
    }
    @TestConfiguration
    @Import(Mailbox.class)
    static class Fixture {
        @Bean ApplicationRunner seedRecovery(MemberRepository members, PasswordEncoder passwords) {
            return args -> {
                for(String role:List.of("buyer","browser")) if(!members.existsByEmail(role+"@example.test"))
                    members.saveAndFlush(Member.builder().email(role+"@example.test")
                        .name("가상 복구 회원").password(passwords.encode("BeforeRecovery123!")).build());
            };
        }
    }
    @RestController
    static class Mailbox {
        private final RecoveryMailSender sender;
        Mailbox(RecoveryMailSender sender) {this.sender=sender;}
        @GetMapping(value="/api/auth/recovery-fixture",produces=MediaType.TEXT_HTML_VALUE)
        ResponseEntity<String> mailbox() {
            var out=new StringBuilder("<!doctype html><html lang=ko><meta charset=UTF-8><meta name=referrer content=no-referrer><title>합성 메일 수신함</title><h1>테스트 전용 수신함</h1>");
            for(var delivery:((MockRecoveryMailSender)sender).deliveries()) out.append("<p><a href=\"").append(delivery.link()).append("\">")
                .append(delivery.recipient().startsWith("browser")?"브라우저":"HTTP").append(" 복구 메일 열기</a></p>");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out.append("</html>").toString());
        }
    }
}
