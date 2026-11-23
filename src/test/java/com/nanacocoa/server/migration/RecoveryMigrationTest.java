package com.nanacocoa.server.migration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryMigrationTest {
    @Test void v13PreservesV12CommerceDataAndRecoveryStateAcrossReconnect() throws Exception {
        String mysql=System.getenv("RECOVERY_MIGRATION_TEST_DB_URL");
        String url=mysql==null?"jdbc:h2:mem:recovery-upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1":mysql;
        String user=mysql==null?"sa":"root";
        Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").target("12").load().migrate();
        try(var connection=DriverManager.getConnection(url,user,"");var sql=connection.createStatement()) {
            sql.executeUpdate("insert into members (id,email,name,password,is_admin,address,phone_number,email_receive_consent,created_at) values (1,'legacy@example.test','synthetic','encoded',false,'original profile','010-0000-0000','N',current_timestamp)");
            sql.executeUpdate("insert into products (id,name,price,summary,detail_title,description,created_at) values (1,'legacy',41000,'synthetic','synthetic','synthetic',current_timestamp)");
            sql.executeUpdate("insert into orders (id,order_id,member_id,total_amount,status,version,recipient_name,phone_number,shipping_address,created_at,updated_at) values (1,'legacy-recovery-order',1,41000,'CREATED',0,'synthetic','010-0000-0000','original order',current_timestamp,current_timestamp)");
            sql.executeUpdate("insert into cart_items (id,member_id,product_id,quantity,version) values (1,1,1,2,0)");
            sql.executeUpdate("insert into shipping_addresses (id,member_id,label,recipient_name,phone_number,postal_code,basic_address,detail_address,version) values (1,1,'home','synthetic','010-0000-0000','12345','original basic','detail',0)");
            sql.executeUpdate("insert into shipping_address_books (member_id,revision,default_address_id) values (1,1,1)");
        }
        var flyway=Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);flyway.validate();
        try(var connection=DriverManager.getConnection(url,user,"");var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("select authentication_generation,address from members where id=1")){
                assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isZero();assertThat(rows.getString(2)).isEqualTo("original profile");
            }
            try(var rows=sql.executeQuery("select shipping_address from orders where id=1")){rows.next();assertThat(rows.getString(1)).isEqualTo("original order");}
            try(var rows=sql.executeQuery("select quantity from cart_items where id=1")){rows.next();assertThat(rows.getInt(1)).isEqualTo(2);}
            try(var rows=sql.executeQuery("select default_address_id from shipping_address_books where member_id=1")){rows.next();assertThat(rows.getLong(1)).isEqualTo(1);}
            sql.executeUpdate("insert into password_recovery_tokens (member_id,token_hash,credential_hash,expires_at,consumed) values (1,repeat('a',64),'encoded','2026-11-22 13:00:00',true)");
            sql.executeUpdate("insert into password_recovery_limits (limit_key,window_start,attempts,next_allowed_at) values ('ip:synthetic','2026-11-22 12:00:00',20,'2026-11-22 12:01:00')");
            sql.executeUpdate("update members set authentication_generation=1 where id=1");
        }
        // 새 연결과 Flyway 인스턴스에서도 이미 사용한 토큰·제한·세대가 유지된다.
        Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").load().validate();
        try(var connection=DriverManager.getConnection(url,user,"");var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("select authentication_generation from members where id=1")){rows.next();assertThat(rows.getLong(1)).isEqualTo(1);}
            try(var rows=sql.executeQuery("select consumed from password_recovery_tokens")){rows.next();assertThat(rows.getBoolean(1)).isTrue();}
            try(var rows=sql.executeQuery("select attempts from password_recovery_limits")){rows.next();assertThat(rows.getInt(1)).isEqualTo(20);}
        }
    }
}
