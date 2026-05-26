#!/usr/bin/env python3
"""
Locale validator — checks that:
1. English strings.xml has no Cyrillic characters (Russian text)
2. Russian strings.xml has no values that are fully English (untranslated)

Returns exit code 1 if any issues found.
"""

import sys
import re
import xml.etree.ElementTree as ET
from pathlib import Path

# Brand names, acronyms, and other terms that are allowed to stay in Latin
# in the Russian locale
ALLOWED_ENGLISH_IN_RU = {
    "HealthConnect Export",
    "Google Drive",
    "Health Connect",
    "Google Play",
    "Google",
    "Bearer",
    "JSON",
    "URL",
    "HTTP",
    "HTTPS",
    "HR",
    "kCal",
    "Act Cal",
    "Avg HR",
    "Dist",
    "Drive",
    "Webhook",
    "webhook",
}

# Patterns for values that are allowed to be fully non-Cyrillic in Russian locale
ALLOWED_PATTERNS_RU = [
    # Emoji-only or mixed emoji (most are caught by is_placeholder_only, safety net)
    re.compile(r'^[📊—…]+'),
]


def extract_strings(xml_path: Path) -> dict[str, str]:
    """Extract string name → value mappings from an Android strings.xml."""
    tree = ET.parse(xml_path)
    root = tree.getroot()
    strings = {}
    for child in root:
        if child.tag == "string":
            name = child.get("name")
            if name:
                strings[name] = child.text or ""
    return strings


def has_cyrillic(text: str) -> bool:
    """Check if text contains any Cyrillic characters."""
    return bool(re.search(r'[А-Яа-яЁё]', text))


def has_latin_alpha(text: str) -> bool:
    """Check if text contains any Latin alphabetic characters."""
    return bool(re.search(r'[A-Za-z]', text))


def is_placeholder_only(text: str) -> bool:
    """Check if the string is purely placeholders, URLs, or format strings."""
    stripped = text.strip()
    if not stripped:
        return True
    # Single placeholder
    if re.match(r'^%[sd\.\d]+$', stripped):
        return True
    # URL
    if re.match(r'^https?://', stripped):
        return True
    # Simple dash/ellipsis
    if stripped in ("—", "…", "sk-…"):
        return True
    return False


def check_english_locale(strings: dict[str, str], path: Path) -> list[str]:
    """Check that English locale has no Cyrillic characters."""
    errors = []
    for name, value in strings.items():
        if has_cyrillic(value):
            errors.append(
                f"{path}: <string name=\"{name}\"> contains Cyrillic: \"{value}\""
            )
    return errors


def check_russian_locale(strings: dict[str, str], en_strings: dict[str, str], path: Path) -> list[str]:
    """Check that Russian locale has no untranslated English strings.

    A string is considered untranslated if:
    - It has a matching English key
    - It contains Latin characters but NO Cyrillic characters
    - It's not a placeholder/URL/brand name that's intentionally kept in Latin
    """
    errors = []
    for name, value in strings.items():
        stripped = value.strip()

        # Skip empty strings and pure placeholders
        if is_placeholder_only(stripped):
            continue

        # If it has Cyrillic, it's definitely translated
        if has_cyrillic(stripped):
            continue

        # If it has no Latin letters at all (e.g. pure numbers/symbols), skip
        if not has_latin_alpha(stripped):
            continue

        # Check if it's an allowed English term
        if stripped in ALLOWED_ENGLISH_IN_RU:
            continue

        # Check allowed patterns
        if any(p.match(stripped) for p in ALLOWED_PATTERNS_RU):
            continue

        # Check if the original English value is the same — likely untranslated
        en_value = en_strings.get(name, "")
        if en_value == value:
            errors.append(
                f"{path}: <string name=\"{name}\"> appears untranslated: \"{value}\""
                f" (matches English value)"
            )
        else:
            errors.append(
                f"{path}: <string name=\"{name}\"> has no Cyrillic: \"{value}\""
                f" (English: \"{en_value}\")"
            )

    return errors


def main():
    project_root = Path(__file__).resolve().parent.parent
    en_xml = project_root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    ru_xml = project_root / "app" / "src" / "main" / "res" / "values-ru" / "strings.xml"

    if not en_xml.exists():
        print(f"ERROR: English strings.xml not found at {en_xml}")
        sys.exit(1)
    if not ru_xml.exists():
        print(f"ERROR: Russian strings.xml not found at {ru_xml}")
        sys.exit(1)

    en_strings = extract_strings(en_xml)
    ru_strings = extract_strings(ru_xml)

    all_errors = []

    # Check English locale for Cyrillic
    en_errors = check_english_locale(en_strings, en_xml)
    all_errors.extend(en_errors)

    # Check Russian locale for untranslated strings
    ru_errors = check_russian_locale(ru_strings, en_strings, ru_xml)
    all_errors.extend(ru_errors)

    # Also check that all EN keys exist in RU (no missing translations)
    missing_keys = set(en_strings.keys()) - set(ru_strings.keys())
    if missing_keys:
        all_errors.append(
            f"{ru_xml}: Missing translations for keys: {', '.join(sorted(missing_keys))}"
        )

    if all_errors:
        print("❌ Locale validation FAILED:")
        for err in all_errors:
            print(f"  • {err}")
        sys.exit(1)
    else:
        en_count = len(en_strings)
        ru_count = len(ru_strings)
        print(f"✅ Locale validation PASSED: {en_count} EN strings, {ru_count} RU strings")
        print(f"   • No Cyrillic in English locale")
        print(f"   • No untranslated strings in Russian locale")
        print(f"   • All keys are present in both locales")
        sys.exit(0)


if __name__ == "__main__":
    main()
