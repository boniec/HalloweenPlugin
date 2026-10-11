import re
import sys

SMALL = {
    'A': 'ᴀ', 'B': 'ʙ', 'C': 'ᴄ', 'D': 'ᴅ', 'E': 'ᴇ', 'F': 'ꜰ', 'G': 'ɢ',
    'H': 'ʜ', 'I': 'ɪ', 'J': 'ᴊ', 'K': 'ᴋ', 'L': 'ʟ', 'M': 'ᴍ', 'N': 'ɴ',
    'O': 'ᴏ', 'P': 'ᴘ', 'Q': 'Q', 'R': 'ʀ', 'S': 'S', 'T': 'ᴛ', 'U': 'ᴜ',
    'V': 'ᴠ', 'W': 'ᴡ', 'X': 'X', 'Y': 'ʏ', 'Z': 'Z',
    'a': 'ᴀ', 'b': 'ʙ', 'c': 'ᴄ', 'd': 'ᴅ', 'e': 'ᴇ', 'f': 'ꜰ', 'g': 'ɢ',
    'h': 'ʜ', 'i': 'ɪ', 'j': 'ᴊ', 'k': 'ᴋ', 'l': 'ʟ', 'm': 'ᴍ', 'n': 'ɴ',
    'o': 'ᴏ', 'p': 'ᴘ', 'q': 'q', 'r': 'ʀ', 's': 's', 't': 'ᴛ', 'u': 'ᴜ',
    'v': 'ᴠ', 'w': 'ᴡ', 'x': 'x', 'y': 'ʏ', 'z': 'ᴢ',
    'ą': 'ą', 'ć': 'ć', 'ę': 'ę', 'ł': 'ł', 'ń': 'ń', 'ó': 'ó', 'ś': 'ś', 'ź': 'ź', 'ż': 'ż',
    'Ą': 'Ą', 'Ć': 'Ć', 'Ę': 'Ę', 'Ł': 'Ł', 'Ń': 'Ń', 'Ó': 'Ó', 'Ś': 'Ś', 'Ź': 'Ź', 'Ż': 'Ż',
}

def to_small(s):
    return ''.join(SMALL.get(c, c) for c in s)

def is_protected(val_stripped):
    """Return True if the entire value should NOT be transformed."""
    if val_stripped == '':
        return True
    # Pure number
    if re.match(r'^-?\d+$', val_stripped):
        return True
    # Pure hex color
    if re.match(r'^#[0-9A-Fa-f]{6}$', val_stripped):
        return True
    if re.match(r'^&#[0-9A-Fa-f]{6}$', val_stripped):
        return True
    # Single color code like &a, &b, etc
    if re.match(r'^&[0-9a-fk-orA-FK-OR]$', val_stripped):
        return True
    # Pure placeholder like %something%
    if re.match(r'^%[^%]+%$', val_stripped):
        return True
    # Single char
    if len(val_stripped) == 1 and val_stripped in '0123456789':
        return True
    return False

def transform_value(val):
    """Transform a YAML value, preserving color codes, hex codes, placeholders."""
    if is_protected(val.strip()):
        return val
    tokens = []
    i = 0
    while i < len(val):
        # %placeholder%
        m = re.match(r'%[^%]+%', val[i:])
        if m:
            tokens.append(m.group(0))
            i += len(m.group(0))
            continue
        # &#RRGGBB or #RRGGBB
        m = re.match(r'&#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{6}', val[i:])
        if m:
            tokens.append(m.group(0))
            i += len(m.group(0))
            continue
        # &X color/format code
        m = re.match(r'&[0-9a-fk-orA-FK-OR]', val[i:])
        if m:
            tokens.append(m.group(0))
            i += len(m.group(0))
            continue
        # Plain text - transform to small caps
        m = re.match(r'[^&%#]+', val[i:])
        if m:
            tokens.append(to_small(m.group(0)))
            i += len(m.group(0))
            continue
        # Single char fallback (shouldn't happen often)
        tokens.append(val[i])
        i += 1
    return ''.join(tokens)

def process_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    out = []
    for line in lines:
        # Comments and blank lines - keep as-is
        if line.startswith('#') or line.strip() == '':
            out.append(line)
            continue

        # List items: - "value" or - value
        m = re.match(r'^(\s*- )(.*)', line)
        if m:
            prefix, val = m.group(1), m.group(2)
            new_val = transform_value(val)
            out.append(prefix + new_val + '\n')
            continue

        # YAML key: value (handles nested keys with indentation)
        m = re.match(r'^(\s*)([A-Za-z0-9_-]+)(:)(.*)', line.rstrip('\n'))
        if m:
            indent, key, colon, val = m.group(1), m.group(2), m.group(3), m.group(4)
            if val.strip() == '':
                # Map key with no value (like "sila:" or "perki:")
                out.append(line)
                continue
            new_val = transform_value(val)
            out.append(indent + key + colon + new_val + '\n')
            continue

        # Anything else - keep as-is
        out.append(line)

    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(out)
    print(f"Small caps applied to {path}")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python smallcaps.py <file.yml>")
        sys.exit(1)
    process_file(sys.argv[1])
