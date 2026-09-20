package io.heckel.ntfy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodeTest {

    // ---------- Positive cases: keyword context ----------

    @Test
    fun `extracts digit code after Chinese keyword`() {
        assertEquals("884512", VerificationCode.extract("您的验证码是 884512，10分钟内有效"))
        assertEquals("666888", VerificationCode.extract("【ntfy】验证码 666888，请勿泄露"))
        assertEquals("552341", VerificationCode.extract("验证码：552341"))
        assertEquals("2026", VerificationCode.extract("验证码 2026")) // keyword context: year-like is fine
    }

    @Test
    fun `extracts digit code after English keyword`() {
        assertEquals("880012", VerificationCode.extract("Your code is 880012"))
        assertEquals("432187", VerificationCode.extract("Your verification code: 432187"))
        assertEquals("918273", VerificationCode.extract("OTP 918273 for login"))
    }

    @Test
    fun `extracts mixed alphanumeric code after keyword`() {
        assertEquals("A1B2C3", VerificationCode.extract("【ntfy】code: A1B2C3"))
        assertEquals("7X9K2M", VerificationCode.extract("Login code: 7X9K2M"))
        assertEquals("X7Y2K9", VerificationCode.extract("Your auth code is X7Y2K9, expires in 5 min"))
    }

    // ---------- Positive cases: generic fallback (no keyword) ----------

    @Test
    fun `extracts plain 4-6 digit code without keyword`() {
        assertEquals("123456", VerificationCode.extract("123456"))
        assertEquals("1357", VerificationCode.extract("Login with 1357 on ntfy.example.com"))
        assertEquals("123456", VerificationCode.extract("G-123456 is your sign-in code")) // letter before '-', not a number group
    }

    @Test
    fun `extracts mixed alphanumeric code without keyword`() {
        assertEquals("7X9K2M", VerificationCode.extract("Your sign-in token 7X9K2M is valid"))
        assertEquals("AB12CD", VerificationCode.extract("Use AB12CD to confirm"))
    }

    // ---------- Negative cases: must NOT match ----------

    @Test
    fun `rejects phone numbers`() {
        assertNull(VerificationCode.extract("您的手机号是 13812345678"))
        assertNull(VerificationCode.extract("Call 010-12345678 for support"))
        assertNull(VerificationCode.extract("Hotline: 400-123-4567"))
        assertNull(VerificationCode.extract("回电 138 1234 5678"))
    }

    @Test
    fun `rejects order numbers and long ids`() {
        assertNull(VerificationCode.extract("订单号 123456789012 已发货"))
        assertNull(VerificationCode.extract("Order 1234567890 shipped today"))
        assertNull(VerificationCode.extract("流水号 ABC123456 已生成")) // 9 chars, too long
    }

    @Test
    fun `rejects years and dates`() {
        assertNull(VerificationCode.extract("Copyright 2026 ntfy"))
        assertNull(VerificationCode.extract("会议时间：2026年9月20日 上午10点"))
        assertNull(VerificationCode.extract("发布日期 2025-06-01"))
    }

    @Test
    fun `rejects money amounts`() {
        assertNull(VerificationCode.extract("今晚消费 ¥1234，欢迎再来"))
        assertNull(VerificationCode.extract("退款 1234 元 已到账"))
        assertNull(VerificationCode.extract("Invoice amount: $5678 due"))
    }

    @Test
    fun `rejects ordinary numbers and text`() {
        assertNull(VerificationCode.extract("版本 1.2.10 已发布"))
        assertNull(VerificationCode.extract("今晚8点见"))
        assertNull(VerificationCode.extract("会议在3号楼5层开"))
        assertNull(VerificationCode.extract("IP address is 192.168.1.1"))
        assertNull(VerificationCode.extract(""))
        assertNull(VerificationCode.extract(null))
        assertNull(VerificationCode.extract("欢迎使用 ntfy！"))
    }

    // ---------- Phase 6: anti-false-positive cases (things that look like codes) ----------

    @Test
    fun `phase6 rejects order and phone numbers with labels`() {
        assertNull(VerificationCode.extract("订单号：1234567890"))
        assertNull(VerificationCode.extract("订单号 1234567890"))
        assertNull(VerificationCode.extract("手机号：13812345678"))
        assertNull(VerificationCode.extract("手机号 13812345678"))
    }

    @Test
    fun `phase6 rejects money amounts`() {
        assertNull(VerificationCode.extract("支付金额：¥1234"))
        assertNull(VerificationCode.extract("支付 ¥1234"))
    }

    @Test
    fun `phase6 rejects version numbers`() {
        assertNull(VerificationCode.extract("当前版本 1.2.10"))
        assertNull(VerificationCode.extract("当前版本：1.2.10"))
    }

    @Test
    fun `phase6 rejects dates`() {
        assertNull(VerificationCode.extract("2026/09/20"))
        assertNull(VerificationCode.extract("日期 2026/09/20"))
        assertNull(VerificationCode.extract("2026-09-20"))
    }

    @Test
    fun `phase6 rejects times`() {
        assertNull(VerificationCode.extract("13:38"))
        assertNull(VerificationCode.extract("会议时间 13:38"))
    }

    @Test
    fun `phase6 rejects plain numbers counted as a quantity`() {
        assertNull(VerificationCode.extract("今天有 123456 人参加活动"))
        assertNull(VerificationCode.extract("共 123456 个订单"))
        assertNull(VerificationCode.extract("已送出 520131 份礼品"))
    }

    @Test
    fun `phase6 rejects shipping and tracking numbers`() {
        assertNull(VerificationCode.extract("快递单号：SF1234567890"))
        assertNull(VerificationCode.extract("快递单号 SF1234567890 已发出"))
        assertNull(VerificationCode.extract("运单号：1234567890123456"))
    }

    @Test
    fun `phase6 six digit plain number is accepted by generic fallback (documented behavior)`() {
        // No keyword and no other context: a 4-6 digit plain number is still accepted by
        // the tier-2 generic fallback (that is the documented, deliberately lenient rule
        // for keyword-less SMS codes). It is NOT unconditional: longer numbers, glued
        // number groups, years, amounts, quantities and explicit non-code labels (see the
        // phase 7 tests) are all rejected.
        assertEquals("884512", VerificationCode.extract("884512"))
        assertEquals("583921", VerificationCode.extract("583921"))
        assertEquals("884512", VerificationCode.extract("Code 884512 arrived"))
    }

    @Test
    fun `phase6 mixed alphanumeric without keyword keeps previous behavior`() {
        // No verification-code context: mixed alphanumeric tokens are accepted by the
        // tier-3 fallback. Phase 7 only narrows this for explicit non-code labels.
        assertEquals("A7K92Q", VerificationCode.extract("A7K92Q"))
        assertEquals("A7K92Q", VerificationCode.extract("Your token A7K92Q is ready"))
    }

    @Test
    fun `phase6 keeps positive keyword cases working`() {
        assertEquals("552010", VerificationCode.extract("验证码：552010"))
        assertEquals("A7K92Q", VerificationCode.extract("验证码：A7K92Q"))
        assertEquals("583921", VerificationCode.extract("OTP: 583921"))
    }

    @Test
    fun `phase6 keeps quantities out while keyword codes still work`() {
        assertEquals("552010", VerificationCode.extract("您的验证码是 552010，10分钟内有效"))
        assertEquals("884512", VerificationCode.extract("您的验证码是 884512，10分钟内有效"))
        assertNull(VerificationCode.extract("今天有 552010 人参加活动"))
    }

    // ---------- Phase 7: explicit non-code labels (order/tracking/phone/account/amount) ----------

    @Test
    fun `phase7 rejects values of explicitly non-code fields`() {
        assertNull(VerificationCode.extract("流水号：884512"))
        assertNull(VerificationCode.extract("账号：A7K92Q"))
        assertNull(VerificationCode.extract("订单号：1234567890"))
        assertNull(VerificationCode.extract("订单编号：123456"))
        assertNull(VerificationCode.extract("交易号：583921"))
        assertNull(VerificationCode.extract("交易编号：A7K92Q"))
        assertNull(VerificationCode.extract("运单号：1234567890123456"))
        assertNull(VerificationCode.extract("快递单号：SF1234567890"))
        assertNull(VerificationCode.extract("手机号：13812345678"))
        assertNull(VerificationCode.extract("金额：884512"))
    }

    @Test
    fun `phase7 rejects other non-code labels and separators`() {
        assertNull(VerificationCode.extract("流水号 884512"))
        assertNull(VerificationCode.extract("账号 A7K92Q"))
        assertNull(VerificationCode.extract("电话：123456"))
        assertNull(VerificationCode.extract("电话号码 583921"))
        assertNull(VerificationCode.extract("用户ID：884512"))
        assertNull(VerificationCode.extract("用户 ID：A7K92Q"))
        assertNull(VerificationCode.extract("用户编号 123456"))
        assertNull(VerificationCode.extract("帐号：583921"))
        assertNull(VerificationCode.extract("价格：884512"))
        assertNull(VerificationCode.extract("物流单号：SF123456789"))
    }

    @Test
    fun `phase7 keeps real verification codes working`() {
        assertEquals("552010", VerificationCode.extract("验证码：552010"))
        assertEquals("A7K92Q", VerificationCode.extract("验证码：A7K92Q"))
        assertEquals("583921", VerificationCode.extract("OTP: 583921"))
        // Generic fallbacks are NOT cancelled
        assertEquals("583921", VerificationCode.extract("583921"))
        assertEquals("A7K92Q", VerificationCode.extract("A7K92Q"))
        assertEquals("552010", VerificationCode.extract("您的验证码是 552010，10分钟内有效"))
    }

    @Test
    fun `phase7 keeps codes that appear elsewhere in a message with a non-code label`() {
        // The non-code label is not adjacent to the actual code, so the code is found
        assertEquals("552010", VerificationCode.extract("流水号 884512，验证码 552010"))
        assertEquals("552010", VerificationCode.extract("订单号 1234567890 已发货，验证码 552010"))
        // The account value is rejected, the real code later in the message is returned
        assertEquals("A1B2C3", VerificationCode.extract("账号：A7K92Q，您的验证码：A1B2C3请查收"))
    }

    @Test
    fun `phase7 does not treat verification wording as a non-code label`() {
        // "电话" inside a sentence must not reject a real code (label is not adjacent)
        assertEquals("552010", VerificationCode.extract("您的电话验证码是 552010"))
        assertEquals("552010", VerificationCode.extract("验证码 552010（请勿告知他人）"))
    }
}
