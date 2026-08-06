from pathlib import Path
import re

p = Path('android-ai-telegram/app/src/main/java/com/starik1917/aitelegramnative/AutoReplyService.kt')
s = p.read_text(encoding='utf-8')

s = s.replace(
    'append("\\n\\nНОВЫЙ ОТВЕТ ВЛАДЕЛЬЦА (только короткое русское сообщение): /no_think")',
    'append("\\n\\nНОВЫЙ ОТВЕТ ВЛАДЕЛЬЦА (только короткое русское сообщение):")'
)
s = s.replace(
    'append("Не обещай денежные переводы, покупки и юридически значимые действия. /no_think")',
    'append("Не обещай денежные переводы, покупки и юридически значимые действия.")'
)

s = s.replace(
'''    private fun isBadOutput(answer: String, latestIncoming: String, previousMine: List<String>): Boolean {
        if (answer.isBlank()) return true
        if (answer.length > MAX_RESPONSE_CHARS) return true
        if (isGarbage(answer)) return true
        val candidates = listOf(latestIncoming) + previousMine
        return candidates.any { tooSimilar(answer, it) }
    }
''',
'''    private fun isBadOutput(answer: String, latestIncoming: String, previousMine: List<String>): Boolean {
        if (answer.isBlank()) return true
        if (answer.length > MAX_RESPONSE_CHARS) return true
        if (containsControlToken(answer)) return true
        if (isGarbage(answer)) return true
        val candidates = listOf(latestIncoming) + previousMine
        return candidates.any { tooSimilar(answer, it) }
    }

    private fun containsControlToken(s: String): Boolean {
        val t = s.trim().lowercase(Locale.ROOT)
        if (t in setOf("/no_think", "/nothink", "/think", "<think>", "</think>", "assistant", "assistant:", "system", "system:", "user", "user:")) return true
        if (Regex("(?i)(?:^|\\s)/(?:no_?think|think)(?:\\s|$)").containsMatchIn(s)) return true
        if (Regex("(?i)<\\|(?:assistant|system|user|im_start|im_end|endoftext)[^>]*\\|>").containsMatchIn(s)) return true
        if (Regex("(?i)</?(?:start_of_turn|end_of_turn|bos|eos|think)>").containsMatchIn(s)) return true
        return false
    }
'''
)

pattern = re.compile(r'    private fun cleanAnswer\(raw: String\): String \{.*?\n    \}\n\n    private fun displayName', re.S)
replacement = r'''    private fun cleanAnswer(raw: String): String {
        var s = raw
        s = s.replace(Regex("(?is)<think>.*?</think>"), "")
        s = s.replace(Regex("(?is)</?think>"), "")
        s = s.replace(Regex("(?i)(?<!\\S)/(?:no_?think|think)(?!\\S)"), "")
        s = s.replace(Regex("(?i)<\\|(?:assistant|system|user|im_start|im_end|endoftext)[^>]*\\|>"), "")
        s = s.replace(Regex("(?i)</?(?:start_of_turn|end_of_turn|bos|eos)>"), "")
        s = s.replace(Regex("(?im)^\\s*(?:###\\s*)?(?:assistant|system|user)\\s*:?\\s*$"), "")
        s = s.trim().trim('"', '“', '”')
        val prefixes = listOf("НОВЫЙ ОТВЕТ ВЛАДЕЛЬЦА:", "Мой следующий ответ:", "Ответ:", "ВЛАДЕЛЕЦ:", "Я:", "assistant:", "model:")
        for (p in prefixes) if (s.startsWith(p, ignoreCase = true)) s = s.substring(p.length).trim()
        s = s.trim()
        if (containsControlToken(s)) return ""
        return s
    }

    private fun displayName'''
s, n = pattern.subn(replacement, s)
if n != 1:
    raise SystemExit(f'cleanAnswer patch failed, matches={n}')

# Ensure the forbidden literal cannot survive in generation prompts.
if ': /no_think' in s or 'actions. /no_think' in s:
    raise SystemExit('prompt still contains /no_think')

p.write_text(s, encoding='utf-8')
print('Patched AutoReplyService.kt: removed /no_think and added control-token filtering')
