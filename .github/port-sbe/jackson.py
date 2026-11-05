# AltoClef reads and writes its settings files with Jackson, which neither loader ships. The companion's AltoClef runs on
# its settings' own defaults (the mod sets what it needs in code), so the port leaves Jackson out: its serializer
# classes are not taken, its annotations and imports go, and ConfigHelper hands back the defaults without a file.
import re

SKIP = {
    'altoclef/util/serialization/' + n + '.java' for n in (
        'AbstractVectorDeserializer', 'AbstractVectorSerializer', 'BlockPosDeserializer', 'BlockPosSerializer',
        'ChunkPosDeserializer', 'ChunkPosSerializer', 'ItemDeserializer', 'ItemSerializer', 'Vec3dDeserializer',
        'Vec3dSerializer')
}

_IMPORT = re.compile(r'^import com\.fasterxml\.jackson\.[^;]*;\n', re.M)
_SERIAL_IMPORT = re.compile(r'^import me\.sailex\.altoclef\.util\.serialization\.(?!IFailableConfigFile|IListConfigFile)[^;]*;\n', re.M)


def _strip_annotations(text):
    out, i = [], 0
    while True:
        m = re.search(r'@Json\w+', text[i:])
        if not m:
            out.append(text[i:])
            return ''.join(out)
        start = i + m.start()
        end = i + m.end()
        # an argument list, balanced
        j = end
        while j < len(text) and text[j] in ' \t':
            j += 1
        if j < len(text) and text[j] == '(':
            depth = 0
            while j < len(text):
                if text[j] == '(':
                    depth += 1
                elif text[j] == ')':
                    depth -= 1
                    if depth == 0:
                        j += 1
                        break
                j += 1
            end = j
        # the line it stood on goes if nothing else is left on it
        line_start = text.rfind('\n', 0, start) + 1
        line_end = text.find('\n', end)
        line_end = len(text) if line_end < 0 else line_end
        if text[line_start:start].strip() == '' and text[end:line_end].strip() == '':
            out.append(text[i:line_start])
            i = line_end + 1
        else:
            out.append(text[i:start])
            i = end


def _replace_method(text, signature, body):
    k = text.find(signature)
    if k < 0:
        raise SystemExit('method not found: ' + signature)
    b = text.find('{', k)
    depth, j = 0, b
    while j < len(text):
        if text[j] == '{':
            depth += 1
        elif text[j] == '}':
            depth -= 1
            if depth == 0:
                break
        j += 1
    return text[:b] + '{\n' + body + '\n   }' + text[j + 1:]


def strip(rel, text):
    if 'com.fasterxml.jackson' not in text and '@Json' not in text and 'util.serialization.' not in text:
        return text
    text = _IMPORT.sub('', text)
    text = _SERIAL_IMPORT.sub('', text)
    text = _strip_annotations(text)
    if rel == 'altoclef/util/helpers/ConfigHelper.java':
        text = _replace_method(text, 'private static <T> T getConfig(',
                               '      // SMH: its settings\' defaults; no file (the mod sets what it needs in code).\n'
                               '      return getDefault.get();')
        text = _replace_method(text, 'public static <T> void saveConfig(',
                               '      // SMH: nothing written (see getConfig).')
        for dead in ('private static void enablePrettyPrinting(', 'private static <T> void writeConfigToFile('):
            text = _replace_method(text, dead, '      // SMH: not used (see getConfig).')
        text = text.replace('ObjectMapper mapper', 'Object mapper').replace('ObjectMapper objectMapper', 'Object objectMapper')
    return text
