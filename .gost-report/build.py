"""Отчёт по работе 1. Сборка: python3 ~/.claude/skills/gost-report/scripts/ensure_env.py .gost-report/build.py

Структура повторяет раздел «Что сдают студенты»: ссылка на репозиторий, текст
README.md, ссылка на последний успешный запуск pipeline. Текст README берётся
прямо из файла, поэтому отчёт всегда совпадает с репозиторием. Титульный лист
содержит только поля из требований к отчёту.
"""
import dataclasses
import os
import re
from datetime import datetime, timezone

# Глобальный конфиг gost-report содержит заглушки преподавателя; все данные титула заданы ниже.
os.environ["GOST_REPORT_CONFIG"] = os.devnull

from docx import Document
from gost_report import ITMO_PROFILE, Report, TitleConfig, paths

STUDENT = "Таджеддинов Р. Э."
GROUP = "P3408"
TITLE = "Разработка защищенного REST API с интеграцией в CI/CD"
REPO = "https://github.com/r4m63/infobez-itmo"
RUN_URL = f"{REPO}/actions/runs/36620490824/job/109584335296"
ROOT = paths().root

# На титуле только: университет, дисциплина, «Работа 1: название», ФИО, группа, год.
# Министерство и город в требованиях не перечислены, поэтому убраны из профиля.
profile = dataclasses.replace(
    ITMO_PROFILE,
    ministry="",
    city="",
    university_full=("Федеральное государственное автономное "
                     "образовательное учреждение высшего образования"),
    university_short="«Национальный исследовательский университет ИТМО»",
    faculty="Дисциплина «Информационная безопасность»",
)

r = Report(TitleConfig(
    work_type="Работа 1:",
    topic="Разработка защищенного REST API\nс интеграцией в CI/CD",
    student_name=STUDENT,
    student_group=GROUP,
    year="2026",
), profile=profile)

# ---------------------------------------------------------------- README.md -> отчёт
INLINE_RULES = [
    (re.compile(r"!\[[^\]]*\]\([^)]*\)"), ""),                          # картинки обрабатываются отдельно
    (re.compile(r"\[([^\]]+)\]\((https?://[^)]+)\)"),
     lambda m: m[2] if m[1] in m[2] else f"{m[1]} ({m[2]})"),           # внешняя ссылка: текст и адрес
    (re.compile(r"\[([^\]]+)\]\([^)]+\)"), r"\1"),                       # ссылка на файл репозитория: текст
    (re.compile(r"<(https?://[^>]+)>"), r"\1"),
    (re.compile(r"\*\*([^*]+)\*\*"), r"\1"),
    (re.compile(r"`([^`]+)`"), r"\1"),
]


def inline(text: str) -> str:
    for pattern, repl in INLINE_RULES:
        text = pattern.sub(repl, text)
    return text.strip()


def cells(row: str) -> list:
    return [inline(c) for c in row.strip().strip("|").split("|")]


def render_readme(r: Report, path) -> None:
    """Переносит README.md в отчёт: ## и ### становятся подразделами, код остаётся текстом."""
    lines = path.read_text(encoding="utf-8").splitlines()
    heading = ""
    para, items, pending_caption = [], [], None
    skip_section = False
    i = 0

    def flush():
        nonlocal para, items
        if para:
            r.text(inline(" ".join(para)))
        if items:
            r.bullet([inline(x) for x in items])
        para, items = [], []

    while i < len(lines):
        raw = lines[i]
        line = raw.strip()
        i += 1
        if line.startswith("# "):                   # название работы уже на титульном листе
            continue
        if line.startswith("## "):
            flush()
            heading = line[3:]
            # Оглавление README состоит из якорных ссылок, в PDF они не работают.
            skip_section = heading == "Содержание"
            if not skip_section:
                r.h2(heading)
            continue
        if skip_section:
            continue
        if line.startswith("### "):
            flush()
            heading = line[4:]
            r.h3(heading)
            continue
        if line.startswith("```"):
            flush()
            code = []
            indent = len(raw) - len(raw.lstrip())
            while i < len(lines) and not lines[i].strip().startswith("```"):
                code.append(lines[i][indent:])
                i += 1
            i += 1
            r.code("\n".join(code))
            continue
        if line.startswith("|"):
            flush()
            rows = [cells(line)]
            while i < len(lines) and lines[i].strip().startswith("|"):
                if not re.fullmatch(r"\|[\s:|-]+\|", lines[i].strip()):
                    rows.append(cells(lines[i]))
                i += 1
            r.table(rows, caption=inline(heading))
            continue
        image = re.fullmatch(r"!\[([^\]]*)\]\(([^)]+)\)", line)
        if image:
            flush()
            n = r.figure(str(ROOT / image[2]), image[1])
            if pending_caption:
                r.text(f"{pending_caption.rstrip('.')} ({r.ref.figure(n)}).")
                pending_caption = None
            continue
        bold_line = re.fullmatch(r"\*\*(.+)\*\*", line)
        if bold_line and not para and not items:
            # Подпись вида «**SAST: 0 замечаний.**» перед картинкой становится ссылкой на рисунок.
            pending_caption = inline(bold_line[1])
            continue
        if not line:
            if para:
                flush()
            continue
        if line.startswith("- "):
            if para:
                flush()
            items.append(line[2:])
        elif raw.startswith(" ") and items:
            items[-1] += " " + line               # продолжение пункта списка
        else:
            if items:
                flush()
            para.append(line)
    flush()


# ---------------------------------------------------------------- 1. Репозиторий
r.h1("Ссылка на репозиторий")
r.text(f"Код проекта размещён в публичном репозитории GitHub: {REPO}.")

# ---------------------------------------------------------------- 2. README.md
r.h1("Текст файла README.md")
render_readme(r, ROOT / "README.md")

# ---------------------------------------------------------------- 3. Pipeline
r.h1("Ссылка на последний успешный запуск pipeline")
r.text(f"Запуск #54 workflow Build and Security Checks: {RUN_URL}.")

out = r.save()

# Метаданные: автор отчёта вместо пустых полей и даты 2000 года из генератора.
doc = Document(out)
props = doc.core_properties
props.author = STUDENT
props.last_modified_by = STUDENT
props.title = f"Работа 1: {TITLE}"
props.created = props.modified = datetime.now(timezone.utc).replace(tzinfo=None)
doc.save(out)
