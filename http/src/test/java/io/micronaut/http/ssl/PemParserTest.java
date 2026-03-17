package io.micronaut.http.ssl;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PemParserTest {
    @Test
    public void pkcs8Rsa() throws Exception {
        List<Object> items = new PemParser(null, null).loadPem("""
            -----BEGIN PRIVATE KEY-----
            MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDQUMBfdIw9YCnu
            FdXDV1cePcXwmc9C9z/BRBK8EmqtGHSGNUjpCHKKGPXJXUseQ7xZNeZdl9CEhZDv
            LTLghIrKJXod/fh0K0FzukJUQwGXBMHxf5FUMUuzZ1FAVHYO44S3Qj+vTMd1BAbK
            jjB+Od9bj5UN9Ab8zG4mTV6wXNX84bsbV6SXqakwR5gJ5GAeOYGf1uHG+o54LobY
            C2857Bsa4bbtleCaXDhJ9K417iNTvP0I/F1C2fQyjwEcOvvI/UHj8nBdCSubiwgv
            JRGv5NbJkZREUAffKiEZwfP1Jeb5/zBdELnlVNk9vjRrXQV5nwzijxFFVNwgC1KW
            TNT2q7xhAgMBAAECggEADUXJ3gMbYNk4zrM8M5Yj5ji+HPx1ztDZUm9riKldO+Ji
            IT9xoexgfOFGfsIXlEnxTRdQsk+nENyAPab59gFn5OWSpGjPPNqZMN4SFhOPXT+H
            umL7/9rktX1smT5QZ9Yx+WmqjFkmX7OmGZ+J4i7+htNNBk6xJ8gAMCdPlEnT9gjP
            1T4o5i7Bxo6bPKW20CKhHCoX6sLy9IyQbOVCm4ZM+/xQBDOIH06IpQzfTBtK8p5z
            GAEmISBJdkIzVydZo++3PW5R33LORCuROLE8eO/FapxxHfXt/HFG67NKJHj0Mxks
            PY3wEE5MMY3uEi4BpMXG4p1YbWAxiWWCcTdzO1t1uQKBgQD7vRuYRjbuLRJzoATR
            6uyknUByxiRnwl1UyXpRNeikktb2h9CRjkkeofDUnR0O7Fc9xlplb+3F8xAnjAYy
            fSG9EygMnnpaVTntkM9jjbfwWOLNL9TkbfkYh5+NXKx4+kGH9x64qY9QYVVx2dIx
            7A9VJGlR/WULHK/Mod9lvqFtWQKBgQDT13jRnhjJSx0u8WnQxmW/P/2aRdL5Dwjj
            Txr38O/d6P2IJ3KxBRdbQDwMXIWyrSg6BHV+/9AefTE2WMt+lRdeJtsfm1sk/hNI
            BEElrnjPBxSUzEW+BfR+C98ziGXkeU63FzETdyRLSyuTDj0HimDUjn07PYHJ+PSM
            +9RSBmg+SQKBgBWEqrEvf06Ip8UebosLC8Nedb3Z1g7QfG3c8wmyE/rNWnakUV2/
            zdxCd3WVIIDADw0GwX9eO/Lpvf3DaFw0egfRdWDuwwKpulPmRvP5jzmKP4jOh+Im
            zF4eabMJsux8Z9GZfBTI2alDSKx8tAz0YrMic6ZAhLyYbSaOx6vIyb+xAoGAXuZl
            Mop/khV7Ql9VxvhJ9m5FPspO49IPaj0y2KXv3tqUUV1SrOiZ+QACposAPfYCUlNT
            C4ywACiXVkBbER7bNAt2GGexYhYMLzPwt1+8sQn791i3WZQzXhAVcnGFK+AIGk9i
            ZdT2xmArahpzzp/4FuCqS1KXhRJpA4uBJynFozkCgYBz+TyFWuhdML06ZEJPxKmC
            e7LHsyxOXZGoIhTqB0XARXMA8gSt4wdrtrN8koQlmiWN5LfotAlfbykV2ZKpCdgX
            eGmFzaDSoenJqIRAiI4OrK2ukrVaW9qD1Vff/md1Uzc1dYwUkWqNp8v7vPx2HQBC
            ldYFCJQ8/XLS7ujXEiv0Ew==
            -----END PRIVATE KEY-----
            """);
        assertEquals(1, items.size());
        RSAPrivateKey key = assertInstanceOf(RSAPrivateKey.class, items.get(0));
        assertEquals(
            new BigInteger("26297387460268743102040457553165546239149094073239005348955226939098208217753988833267081884139652198696702484480896788225411092939518395692664601066047035464528971551454681842404423845080452443932201241792678847434609059497058970345580274942990400089461450945614514109876737615683212442747283594923682775945848729148001127174636677520541003716154359434674620933723722605237477921059532399313874922023205627380134050262110566006600639431959394448635199072282414496382001596281606276405706915358620713649067936308458079204732199243773136651525293676965024281822823055882102207432170133026926355690324977137843826310241"),
            key.getModulus());
    }

    @Test
    public void pkcs1Rsa() throws Exception {
        List<Object> items = new PemParser(null, null).loadPem("""
            -----BEGIN RSA PRIVATE KEY-----
            MIIEogIBAAKCAQEA0FDAX3SMPWAp7hXVw1dXHj3F8JnPQvc/wUQSvBJqrRh0hjVI
            6Qhyihj1yV1LHkO8WTXmXZfQhIWQ7y0y4ISKyiV6Hf34dCtBc7pCVEMBlwTB8X+R
            VDFLs2dRQFR2DuOEt0I/r0zHdQQGyo4wfjnfW4+VDfQG/MxuJk1esFzV/OG7G1ek
            l6mpMEeYCeRgHjmBn9bhxvqOeC6G2AtvOewbGuG27ZXgmlw4SfSuNe4jU7z9CPxd
            Qtn0Mo8BHDr7yP1B4/JwXQkrm4sILyURr+TWyZGURFAH3yohGcHz9SXm+f8wXRC5
            5VTZPb40a10FeZ8M4o8RRVTcIAtSlkzU9qu8YQIDAQABAoIBAA1Fyd4DG2DZOM6z
            PDOWI+Y4vhz8dc7Q2VJva4ipXTviYiE/caHsYHzhRn7CF5RJ8U0XULJPpxDcgD2m
            +fYBZ+TlkqRozzzamTDeEhYTj10/h7pi+//a5LV9bJk+UGfWMflpqoxZJl+zphmf
            ieIu/obTTQZOsSfIADAnT5RJ0/YIz9U+KOYuwcaOmzylttAioRwqF+rC8vSMkGzl
            QpuGTPv8UAQziB9OiKUM30wbSvKecxgBJiEgSXZCM1cnWaPvtz1uUd9yzkQrkTix
            PHjvxWqccR317fxxRuuzSiR49DMZLD2N8BBOTDGN7hIuAaTFxuKdWG1gMYllgnE3
            cztbdbkCgYEA+70bmEY27i0Sc6AE0erspJ1AcsYkZ8JdVMl6UTXopJLW9ofQkY5J
            HqHw1J0dDuxXPcZaZW/txfMQJ4wGMn0hvRMoDJ56WlU57ZDPY4238FjizS/U5G35
            GIefjVysePpBh/ceuKmPUGFVcdnSMewPVSRpUf1lCxyvzKHfZb6hbVkCgYEA09d4
            0Z4YyUsdLvFp0MZlvz/9mkXS+Q8I408a9/Dv3ej9iCdysQUXW0A8DFyFsq0oOgR1
            fv/QHn0xNljLfpUXXibbH5tbJP4TSARBJa54zwcUlMxFvgX0fgvfM4hl5HlOtxcx
            E3ckS0srkw49B4pg1I59Oz2Byfj0jPvUUgZoPkkCgYAVhKqxL39OiKfFHm6LCwvD
            XnW92dYO0Hxt3PMJshP6zVp2pFFdv83cQnd1lSCAwA8NBsF/Xjvy6b39w2hcNHoH
            0XVg7sMCqbpT5kbz+Y85ij+IzofiJsxeHmmzCbLsfGfRmXwUyNmpQ0isfLQM9GKz
            InOmQIS8mG0mjseryMm/sQKBgF7mZTKKf5IVe0JfVcb4SfZuRT7KTuPSD2o9Mtil
            797alFFdUqzomfkAAqaLAD32AlJTUwuMsAAol1ZAWxEe2zQLdhhnsWIWDC8z8Ldf
            vLEJ+/dYt1mUM14QFXJxhSvgCBpPYmXU9sZgK2oac86f+BbgqktSl4USaQOLgScp
            xaM5AoGAc/k8hVroXTC9OmRCT8Spgnuyx7MsTl2RqCIU6gdFwEVzAPIEreMHa7az
            fJKEJZoljeS36LQJX28pFdmSqQnYF3hphc2g0qHpyaiEQIiODqytrpK1Wlvag9VX
            3/5ndVM3NXWMFJFqjafL+7z8dh0AQpXWBQiUPP1y0u7o1xIr9BM=
            -----END RSA PRIVATE KEY-----""");
        assertEquals(1, items.size());
        RSAPrivateKey key = assertInstanceOf(RSAPrivateKey.class, items.get(0));
        assertEquals(
            new BigInteger("26297387460268743102040457553165546239149094073239005348955226939098208217753988833267081884139652198696702484480896788225411092939518395692664601066047035464528971551454681842404423845080452443932201241792678847434609059497058970345580274942990400089461450945614514109876737615683212442747283594923682775945848729148001127174636677520541003716154359434674620933723722605237477921059532399313874922023205627380134050262110566006600639431959394448635199072282414496382001596281606276405706915358620713649067936308458079204732199243773136651525293676965024281822823055882102207432170133026926355690324977137843826310241"),
            key.getModulus());
    }

    @Test
    public void pkcs8RsaEncrypted() throws Exception {
        List<Object> items = new PemParser(null, "test").loadPem("""
            -----BEGIN ENCRYPTED PRIVATE KEY-----
            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
            -----END ENCRYPTED PRIVATE KEY-----""");
        assertEquals(1, items.size());
        RSAPrivateKey key = assertInstanceOf(RSAPrivateKey.class, items.get(0));
        assertEquals(
            new BigInteger("26297387460268743102040457553165546239149094073239005348955226939098208217753988833267081884139652198696702484480896788225411092939518395692664601066047035464528971551454681842404423845080452443932201241792678847434609059497058970345580274942990400089461450945614514109876737615683212442747283594923682775945848729148001127174636677520541003716154359434674620933723722605237477921059532399313874922023205627380134050262110566006600639431959394448635199072282414496382001596281606276405706915358620713649067936308458079204732199243773136651525293676965024281822823055882102207432170133026926355690324977137843826310241"),
            key.getModulus());
    }

    @Test
    public void pkcs8Ec() throws Exception {
        List<Object> items = new PemParser(null, null).loadPem("""
            -----BEGIN PRIVATE KEY-----
            MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgbsCIeD2F110ZVzZI
            KSSZ+96uasIw1LmMA+5OPexz0gahRANCAARF/wP8UhdiQFqdlKck8H9b6PpgYfpB
            TgURPRWudxoA1vlKYYxT09FXyL0OOit5GSn/N1f9hBKT42sJ7nParbMi
            -----END PRIVATE KEY-----
            """);
        assertEquals(1, items.size());
        ECPrivateKey key = assertInstanceOf(ECPrivateKey.class, items.get(0));
        assertEquals(
            new BigInteger("6EC088783D85D75D19573648292499FBDEAE6AC230D4B98C03EE4E3DEC73D206", 16),
            key.getS());
    }

    @Test
    public void pkcs8EcEncrypted() throws Exception {
        List<Object> items = new PemParser(null, "test").loadPem("""
            -----BEGIN ENCRYPTED PRIVATE KEY-----
            MIHsMFcGCSqGSIb3DQEFDTBKMCkGCSqGSIb3DQEFDDAcBAjg1EB+ARfutQICCAAw
            DAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEEAhVYdST1gTXVLoOtJGRLdEEgZBN
            rwo6vVFu/87G30sn2VJK97OKJTDBQOY52Sz/EUDqgpxFWmz7+DLhjN6YUCi+HO4r
            lgoMk1J1pQLp7zZzaronV3P8tPo+LVyLN6sohGUvzAis1l/q59eokTPm1zl1T5f+
            ZX3k3LQLdWEK8ifI66ssIrO5P2ksSxGll4hPz0fkWzH2mKAD5sBJ5vyoJ3mjqbI=
            -----END ENCRYPTED PRIVATE KEY-----
            """);
        assertEquals(1, items.size());
        ECPrivateKey key = assertInstanceOf(ECPrivateKey.class, items.get(0));
        assertEquals(
            new BigInteger("6EC088783D85D75D19573648292499FBDEAE6AC230D4B98C03EE4E3DEC73D206", 16),
            key.getS());
    }

    @Test
    public void pkcs1Ec() throws Exception {
        List<Object> items = new PemParser(null, null).loadPem("""
            -----BEGIN EC PRIVATE KEY-----
            MHcCAQEEIG7AiHg9hdddGVc2SCkkmfvermrCMNS5jAPuTj3sc9IGoAoGCCqGSM49
            AwEHoUQDQgAERf8D/FIXYkBanZSnJPB/W+j6YGH6QU4FET0VrncaANb5SmGMU9PR
            V8i9DjoreRkp/zdX/YQSk+NrCe5z2q2zIg==
            -----END EC PRIVATE KEY-----
            """);
        assertEquals(1, items.size());
        ECPrivateKey key = assertInstanceOf(ECPrivateKey.class, items.get(0));
        assertEquals(
            new BigInteger("6EC088783D85D75D19573648292499FBDEAE6AC230D4B98C03EE4E3DEC73D206", 16),
            key.getS());
    }
}
