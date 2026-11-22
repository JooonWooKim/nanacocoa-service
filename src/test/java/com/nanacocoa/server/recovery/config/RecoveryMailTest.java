package com.nanacocoa.server.recovery.config;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import static org.assertj.core.api.Assertions.*;

class RecoveryMailTest {
    RecoveryProperties properties(int port) {
        var p=new RecoveryProperties();p.setEnabled(true);p.setSmtpEnabled(true);p.setBaseUrl("https://recovery.example.test");
        p.getSmtp().setHost("127.0.0.1");p.getSmtp().setPort(port);p.getSmtp().setFrom("sender@example.test");
        p.getSmtp().setAllowPlaintextLoopback(true);return p;
    }
    StandardEnvironment environment(boolean test) {
        var e=new StandardEnvironment();if(test)e.setActiveProfiles("test");return e;
    }
    @Test void smtpAdapterDeliversToIsolatedReceiverWithoutTransaction() throws Exception {
        try(var smtp=new Receiver(false,false)) {
            var sender=new RecoveryConfiguration().recoveryMailSender(properties(smtp.port()),environment(true));
            sender.send("synthetic@example.test","https://recovery.example.test/password-reset.html#token=synthetic");
            String message=smtp.message.get(2,TimeUnit.SECONDS);
            var mime=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()),
                    new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
            assertThat(mime.getContent().toString()).contains("#token=synthetic");
        }
    }
    @Test void smtpRejectionAndTimeoutReturnFailuresWithoutAutomaticResend() throws Exception {
        for(boolean timeout:new boolean[]{false,true}) {
            try(var smtp=new Receiver(!timeout,timeout)) {
                var sender=new RecoveryConfiguration().recoveryMailSender(properties(smtp.port()),environment(true));
                assertThatThrownBy(()->sender.send("synthetic@example.test","https://recovery.example.test/#token=synthetic"))
                        .isInstanceOf(org.springframework.mail.MailException.class);
                assertThat(smtp.connections).isEqualTo(1);
            }
        }
    }
    @Test void untrustedUrlsMockInProductionAndInsecureSmtpAreRejected() {
        var config=new RecoveryConfiguration();
        for(String url:new String[]{"http://public.example.test","https://user:pass@example.test","https://example.test/#fragment","https://example.test/?query"}) {
            var p=properties(1);p.setBaseUrl(url);
            assertThatThrownBy(()->config.recoveryMailSender(p,environment(true))).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(()->config.recoveryMailSender(properties(1),environment(false))).isInstanceOf(IllegalStateException.class);
        var mock=new RecoveryProperties();mock.setMode("mock");
        assertThatThrownBy(()->config.recoveryMailSender(mock,environment(false))).isInstanceOf(IllegalStateException.class);
    }
    static final class Receiver implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
        final ExecutorService worker=Executors.newSingleThreadExecutor();
        final CompletableFuture<String> message=new CompletableFuture<>();
        volatile int connections;
        volatile Socket socket;
        Receiver(boolean reject, boolean timeout) throws Exception {
            worker.submit(()->{
                try(var s=server.accept()) {
                    socket=s;connections++;
                    if(timeout){Thread.sleep(6000);return;}
                    var in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
                    var out=new PrintWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8),true);
                    out.print("220 localhost test receiver\r\n");out.flush();String line;boolean data=false;var body=new StringBuilder();
                    while((line=in.readLine())!=null) {
                        String reply="250 OK";
                        if(data){if(line.equals(".")){data=false;message.complete(body.toString());}else{body.append(line).append('\n');continue;}}
                        else if(line.startsWith("RCPT")&&reject)reply="550 synthetic rejection";
                        else if(line.startsWith("DATA")){data=true;reply="354 send data";}
                        else if(line.startsWith("QUIT")){out.print("221 bye\r\n");out.flush();break;}
                        out.print(reply+"\r\n");out.flush();
                    }
                }catch(Exception e){message.completeExceptionally(e);}
            });
        }
        int port(){return server.getLocalPort();}
        public void close() throws Exception {if(socket!=null)socket.close();server.close();worker.shutdownNow();}
    }
}
