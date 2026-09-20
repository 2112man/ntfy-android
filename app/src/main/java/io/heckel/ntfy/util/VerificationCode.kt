package io.heckel.ntfy.util

/**
 * Extracts verification codes (e.g. SMS/2FA codes) from notification messages.
 *
 * The goal is to reliably find codes like "123456", "A1B2C3" or "G-123456" while
 * avoiding false positives such as phone numbers, order numbers, years, dates,
 * amounts of money and version numbers.
 *
 * Strategy (two tiers, first match wins):
 *
 * 1. Keyword context: a token that appears right after a keyword such as
 *    "验证码"/"校验码"/"code"/"OTP" is trusted more, so the rules are relaxed
 *    (digit-only codes of 3-8 chars, mixed alphanumeric codes of 3-10 chars).
 * 2. Generic fallback (no keyword): only accept
 *    - digit-only codes of 4-6 digits, that are not year-like (1900-2099) and
 *      not directly preceded by a year character ("2026年"), and
 *    - mixed alphanumeric codes of 5-8 chars containing both digits and letters.
 *
 * A candidate is rejected if it looks like:
 * - part of a longer number group ("400-123-4567" -> "4567" is rejected)
 * - a phone-like or order-like long number (> 8 digits)
 * - an amount of money or a quantity ("¥1234", "1234元", "123456 人")
 * - the value of an explicitly non-code field, i.e. directly preceded by a label
 *   such as "订单号", "流水号", "运单号", "手机号", "账号" or "金额"
 *   ("流水号：884512", "账号：A7K92Q")
 *
 * This class is pure Kotlin without Android dependencies, so it is fully unit-testable.
 */
object VerificationCode {

    // Keyword context: code candidate right after a known keyword
    private const val KEYWORDS =
        "验证码|校验码|确认码|动态码|授权码|安全码|口令码|otp|passcode|verification\\s*code|security\\s*code|code"
    private val KEYWORD_CONTEXT_PATTERN = Regex(
        "(?<![A-Za-z])(?:$KEYWORDS)[^A-Za-z0-9]{0,10}([A-Za-z0-9]{3,10})",
        RegexOption.IGNORE_CASE
    )

    // Generic fallback patterns
    private val DIGIT_CODE_PATTERN = Regex("(?<![0-9A-Za-z])([0-9]{4,6})(?![0-9A-Za-z])")
    private val MIXED_CODE_PATTERN = Regex("(?<![A-Za-z0-9])([A-Za-z0-9]{5,8})(?![A-Za-z0-9])")

    // Characters that may glue a candidate to a longer number, e.g. "400-123-4567"
    private const val NUMBER_SEPARATORS = " -./"
    private val CURRENCY_BEFORE = setOf('¥', '￥', '$', '＄', '€', '£')

    // Units/currency right after a number mark it as an amount or a quantity, e.g.
    // "1234元", "¥1234", or "今天有 123456 人参加活动". Verification codes are never
    // directly followed by a Chinese measure word, so these are rejected.
    private val UNIT_AFTER = setOf(
        '元', '圆', '块', '円', // money
        '人', '个', '名', '位', '次', '岁', '件', '台', '张', '份', '笔', '条', '项',
        '组', '批', '种', '页', '只', '辆', '瓶', '包', '枚', '颗', '根', '支', '双',
        '套', '部', '点', '倍', '层', '楼', '期', '周', '秒'
    )

    // Labels that clearly mark a value as some other business field (order number,
    // tracking number, phone number, account, amount, ...). A candidate directly
    // after one of these is rejected, e.g. "流水号：884512" or "账号：A7K92Q".
    // The label must be adjacent to the candidate (only up to 4 non-alphanumeric
    // characters in between), so a code elsewhere in the same message still works:
    // "流水号 884512，验证码 552010" -> "552010".
    private const val REJECT_KEYWORDS =
        "订单编号|订单号|流水号|交易编号|交易号|快递单号|快递编号|运单号|物流单号|" +
            "手机号码|手机号|电话号码|电话|用户编号|用户\\s?ID|用户id|账号|帐号|账户|金额|价格"
    private val REJECT_CONTEXT_PATTERN = Regex(
        "(?:$REJECT_KEYWORDS)[^A-Za-z0-9]{0,4}$",
        RegexOption.IGNORE_CASE
    )

    // How far back (in characters) we look for a rejecting label
    private const val REJECT_CONTEXT_WINDOW = 16

    /**
     * Returns the first verification code found in [message], or null if the
     * message does not appear to contain one.
     */
    fun extract(message: String?): String? {
        if (message.isNullOrBlank()) {
            return null
        }
        val text = message.trim()
        findAfterKeyword(text)?.let { return it }
        findGenericDigitCode(text)?.let { return it }
        findGenericMixedCode(text)?.let { return it }
        return null
    }

    /**
     * Tier 1: a token directly following a keyword. Relaxed rules:
     * digit-only 3-8 chars, or mixed alphanumeric 3-10 chars with both digits and letters.
     */
    private fun findAfterKeyword(text: String): String? {
        for (match in KEYWORD_CONTEXT_PATTERN.findAll(text)) {
            val token = match.groupValues[1]
            if (!isAlphanumeric(token)) {
                continue
            }
            val tokenRange = match.groups[1]?.range ?: continue
            val tokenStart = tokenRange.first
            val tokenEnd = tokenRange.last + 1
            if (isGluedToLongerNumber(text, tokenStart, tokenEnd) || isMoneyOrDateLike(text, tokenStart, tokenEnd)) {
                continue
            }
            if (isRejectedContext(text, tokenStart)) {
                continue
            }
            val hasDigit = token.any { it.isDigit() }
            val hasLetter = token.any { it.isLetter() }
            val valid = if (hasLetter) {
                hasDigit && token.length in 3..10 // mixed code, e.g. "A1B2C3"
            } else {
                token.length in 3..8 // digit-only code, e.g. "123456"
            }
            if (valid) {
                return token
            }
        }
        return null
    }

    /**
     * Tier 2: digit-only 4-6 chars without a keyword. Rejects year-like numbers
     * and numbers glued to other numbers (phone/order number fragments).
     */
    private fun findGenericDigitCode(text: String): String? {
        for (match in DIGIT_CODE_PATTERN.findAll(text)) {
            val token = match.groupValues[1]
            val start = match.range.first
            val end = match.range.last + 1
            if (isGluedToLongerNumber(text, start, end) || isMoneyOrDateLike(text, start, end)) {
                continue
            }
            if (isRejectedContext(text, start)) {
                continue
            }
            if (isYearLike(token)) {
                continue
            }
            return token
        }
        return null
    }

    /**
     * Tier 3: mixed alphanumeric 5-8 chars (both digits and letters) without a keyword.
     * These are quite distinctive and rarely appear as phone/order numbers.
     */
    private fun findGenericMixedCode(text: String): String? {
        for (match in MIXED_CODE_PATTERN.findAll(text)) {
            val token = match.groupValues[1]
            val start = match.range.first
            val end = match.range.last + 1
            if (isGluedToLongerNumber(text, start, end) || isMoneyOrDateLike(text, start, end)) {
                continue
            }
            if (isRejectedContext(text, start)) {
                continue
            }
            val hasDigit = token.any { it.isDigit() }
            val hasLetter = token.any { it.isLetter() }
            if (hasDigit && hasLetter) {
                return token
            }
        }
        return null
    }

    /**
     * True if the candidate starting at [start] is directly preceded by a label
     * that marks it as a different business field (order/tracking/phone/account/
     * amount...), e.g. "流水号：884512".
     */
    private fun isRejectedContext(text: String, start: Int): Boolean {
        if (start <= 0) {
            return false
        }
        val from = maxOf(0, start - REJECT_CONTEXT_WINDOW)
        return REJECT_CONTEXT_PATTERN.containsMatchIn(text.substring(from, start))
    }

    private fun isAlphanumeric(token: String): Boolean {
        return token.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }
    }

    /**
     * "400-123-4567" -> "4567" is preceded by "-" which is preceded by a digit,
     * so it is part of a longer number group and must be rejected.
     */
    private fun isGluedToLongerNumber(text: String, start: Int, end: Int): Boolean {
        if (start >= 2) {
            val separator = text[start - 1]
            if (separator in NUMBER_SEPARATORS && text[start - 2].isDigit()) {
                return true
            }
        }
        if (end + 1 < text.length) {
            val separator = text[end]
            if (separator in NUMBER_SEPARATORS && text[end + 1].isDigit()) {
                return true
            }
        }
        return false
    }

    /**
     * Rejects amounts and quantities ("¥1234", "1234元", "退款 1234 元",
     * "今天有 123456 人参加活动") and dates ("2026年").
     */
    private fun isMoneyOrDateLike(text: String, start: Int, end: Int): Boolean {
        if (start > 0 && text[start - 1] in CURRENCY_BEFORE) {
            return true
        }
        if (end < text.length) {
            val next = text[end]
            if (next in UNIT_AFTER || next == '年') {
                return true
            }
            if (next == ' ' && end + 1 < text.length && text[end + 1] in UNIT_AFTER) {
                return true // "1234 元"
            }
        }
        return false
    }

    private fun isYearLike(token: String): Boolean {
        val value = token.toIntOrNull() ?: return false
        return value in 1900..2099
    }
}
