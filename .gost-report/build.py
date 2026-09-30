"""Отчёт по работе 1. Сборка: python3 ~/.claude/skills/gost-report/scripts/ensure_env.py .gost-report/build.py

Структура повторяет раздел «Что сдают студенты»: ссылка на репозиторий, текст
README.md, ссылка на последний успешный запуск pipeline. Текст README берётся
прямо из файла, поэтому отчёт всегда совпадает с репозиторием. Титульный лист
содержит только поля из требований к отчёту.
"""
import copy
import dataclasses
import os
import re
from datetime import datetime, timezone

# Глобальный конфиг gost-report содержит заглушки преподавателя; все данные титула заданы ниже.
os.environ["GOST_REPORT_CONFIG"] = os.devnull

from docx import Document
from docx.opc.constants import RELATIONSHIP_TYPE as RT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
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
# Текст ссылки -> адрес. Заполняется при разборе README, после сборки такие
# фрагменты превращаются в гиперссылки Word.
LINKS = {}


def link(text: str, target: str) -> str:
    url = target if target.startswith("http") else f"{REPO}/blob/main/{target}"
    LINKS[text] = url
    return text


INLINE_RULES = [
    (re.compile(r"!\[[^\]]*\]\([^)]*\)"), ""),                          # картинки обрабатываются отдельно
    (re.compile(r"\[([^\]]+)\]\(([^)]+)\)"), lambda m: link(m[1], m[2])),  # ссылка: остаётся текст
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


def make_links_clickable(doc) -> None:
    """Оборачивает адреса и тексты ссылок из README в гиперссылки Word."""
    names = sorted(LINKS, key=len, reverse=True)
    pattern = re.compile(r"https?://[^\s)]+[^\s).,]" +
                         "".join(f"|{re.escape(n)}" for n in names))
    for paragraph in doc.paragraphs:
        for run in list(paragraph.runs):
            texts = run._r.findall(qn("w:t"))
            if len(texts) != 1 or len(run._r) > 2 or not pattern.search(run.text):
                continue                        # только простые однострочные runs
            text, pos = run.text, 0
            for m in pattern.finditer(text):
                if m.start() > pos:
                    run._r.addprevious(_run_like(run, text[pos:m.start()]))
                url = LINKS.get(m[0], m[0])
                hyperlink = OxmlElement("w:hyperlink")
                hyperlink.set(qn("r:id"), paragraph.part.relate_to(url, RT.HYPERLINK, is_external=True))
                hyperlink.append(_run_like(run, m[0], link_style=True))
                run._r.addprevious(hyperlink)
                pos = m.end()
            if pos < len(text):
                run._r.addprevious(_run_like(run, text[pos:]))
            run._r.getparent().remove(run._r)


def _run_like(run, text: str, link_style: bool = False):
    r = copy.deepcopy(run._r)
    t = r.find(qn("w:t"))
    t.text = text
    t.set(qn("xml:space"), "preserve")
    if link_style:
        rpr = r.find(qn("w:rPr"))
        if rpr is None:
            rpr = OxmlElement("w:rPr")
            r.insert(0, rpr)
        color, underline = OxmlElement("w:color"), OxmlElement("w:u")
        color.set(qn("w:val"), "0563C1")
        underline.set(qn("w:val"), "single")
        rpr.append(color)
        rpr.append(underline)
    return r


make_links_clickable(doc)
doc.save(out)
