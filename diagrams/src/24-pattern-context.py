#!/usr/bin/env python3
"""Pattern context diagram (diagram as code): the system in its context, with numbered design-pattern
badges placed on the components where each pattern is applied. Every badge and legend entry is a
hyperlink to the slide that explains the pattern.

Usage:  python3 24-pattern-context.py ../svg/24-pattern-context.svg
        (PNG for the slides:  rsvg-convert -w 2400 ../svg/24-pattern-context.svg -o ../png/24-pattern-context.png)

Edit PATTERNS (number, name, slide) or COMPONENTS (tier, box, label, badges); no other code needs to change.
"""
import sys
from html import escape

DECK = "../../build/ooad-3tier-patterns.html#"   # links resolve from diagrams/svg/

# Pattern number -> (name, slide number in the deck). Numbers follow the order the slides discuss them.
PATTERNS = {
    1: ("Entity-Control-Boundary", 23),
    2: ("Facade", 24),
    3: ("Strategy + Factory", 25),
    4: ("Abstract Factory", 26),
    5: ("Singleton", 27),
    6: ("Proxy + Chain of Responsibility", 28),
    7: ("DAO + Template Method + Exception Translation", 29),
    8: ("Observer", 30),
    9: ("Adapter", 31),
    10: ("Decorator (Idempotent Receiver)", 38),
    11: ("Front Controller", 33),
    12: ("Identity Map, Unit of Work, Optimistic Lock, Metadata Mapping", 33),
    13: ("DTO + Problem Details", 38),
    14: ("Domain Model (GRASP Expert, Creator)", 9),
    15: ("Layers + Ports and Adapters", 15),
}

W, H = 1600, 1060
BLUE, INK, MUTED, ORANGE, ORANGE_BG = "#1E4E8C", "#1F2933", "#5F6B7A", "#B5651D", "#FBEBD9"
FONT = "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"

# Tier bands: (title, y, height)
TIERS = {
    "web": ("Presentation tier — edu.ooad.joballoc.web", 200, 190),
    "business": ("Business tier — business, domain", 410, 200),
    "persistence": ("Persistence tier — persistence", 630, 200),
}

# id: (x, y, w, h, label lines, [(badge number, caption)])
COMPONENTS = {
    "hxa": (60, 250, 200, 56, ["HttpExchangeAdapter"], [(9, "Adapter")]),
    "fc": (300, 250, 230, 56, ["FrontController", "Router · ErrorMapper"], [(11, "Front Controller")]),
    "idh": (570, 250, 190, 56, ["IdempotentHandler"], [(10, "Decorator")]),
    "ctl": (800, 250, 230, 56, ["BoardController", "ApiController"], [(1, "ECB: control")]),
    "view": (1070, 250, 190, 56, ["BoardView · LoginView"], [(1, "ECB: boundary")]),
    "dto": (1300, 250, 230, 56, ["DTOs · ProblemDTO"], [(13, "DTO, Problem Details")]),
    "fac": (60, 460, 200, 56, ["AllocationService", "«facade»"], [(2, "Facade")]),
    "px": (300, 460, 230, 56, ["SecuredTransactionalProxy", "+ interceptors"], [(6, "Proxy, Chain of Resp.")]),
    "svc": (570, 460, 230, 56, ["AllocationServiceImpl", "BusinessTier «factory»"], [(3, "Factory")]),
    "pol": (840, 460, 200, 56, ["AllocationPolicy", "PolicyFactory"], [(3, "Strategy")]),
    "ev": (1080, 460, 200, 56, ["DomainEventPublisher", "listeners"], [(8, "Observer")]),
    "dom": (1320, 460, 210, 56, ["Job · Worker · Allocation"], [(14, "Domain Model"), (1, "ECB: entity")]),
    "daof": (60, 680, 220, 56, ["DAOFactory", "DAO interfaces"], [(4, "Abstract Factory")]),
    "dao": (320, 680, 220, 56, ["AbstractOrmDAO · Orm*DAO"], [(7, "DAO, Template Method")]),
    "orm": (580, 680, 230, 56, ["TransactionManager", "EntityMetadata"], [(12, "PoEAA persistence")]),
    "pool": (850, 680, 170, 56, ["ConnectionPool"], [(5, "Singleton")]),
    "adp": (1110, 680, 250, 56, ["TransactionManagerAdapter", "JdbcDatabaseHealthAdapter"], [(9, "Adapter (ports)")]),
}

# Flows (from, to)
FLOWS = [("hxa", "fc"), ("fc", "idh"), ("idh", "ctl"), ("ctl", "view"),
         ("fac", "px"), ("px", "svc"), ("svc", "pol"), ("daof", "dao"), ("dao", "orm"), ("orm", "pool")]
DOWN = [("ctl", "fac"), ("svc", "daof")]
# Flows routed below the boxes of a band (to avoid crossing the boxes in between): (from, to, depth)
UNDER = [("ctl", "dto", 46), ("svc", "ev", 46), ("svc", "dom", 62), ("adp", "orm", 52)]


def text(x, y, s, size=14, weight="normal", fill=INK, anchor="middle"):
    return (f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}">{escape(s)}</text>')


def link(n, inner):
    name, slide = PATTERNS[n]
    return (f'<a href="{DECK}{slide}" target="_top"><title>{n}. {escape(name)} (slide {slide})</title>'
            f'{inner}</a>')


def badge(cx, cy, n):
    return link(n, f'<circle cx="{cx}" cy="{cy}" r="13" fill="{ORANGE}"/>'
                   + text(cx, cy + 5, str(n), 13, "bold", "#FFFFFF"))


def center(c):
    x, y, w, h = COMPONENTS[c][:4]
    return x + w / 2, y + h / 2


def arrow(a, b, vertical=False):
    ax, ay, aw, ah = COMPONENTS[a][:4]
    bx, by, bw, bh = COMPONENTS[b][:4]
    if vertical:
        x1, y1 = ax + aw / 2, ay + ah + 40
        x2, y2 = bx + bw / 2, by - 8
        path = f"M{x1},{y1 - 40} L{x1},{(y1 + y2) / 2} L{x2},{(y1 + y2) / 2} L{x2},{y2}"
    else:
        same_row = abs(ay - by) < 5
        if same_row and bx > ax:
            path = f"M{ax + aw},{ay + ah / 2} L{bx - 4},{by + bh / 2}"
        elif same_row:
            path = f"M{ax},{ay + ah / 2} L{bx + bw + 4},{by + bh / 2}"
        else:
            path = f"M{ax + aw / 2},{ay} L{bx + bw / 2},{by + bh + 4}"
    return f'<path d="{path}" fill="none" stroke="{MUTED}" stroke-width="1.6" marker-end="url(#arrow)"/>'


def under(a, b, depth):
    ax, ay, aw, ah = COMPONENTS[a][:4]
    bx, by, bw, bh = COMPONENTS[b][:4]
    x1, x2 = ax + aw / 2 + 30, bx + bw / 2 + 30
    y = ay + ah + depth
    return (f'<path d="M{x1},{ay + ah} L{x1},{y} L{x2},{y} L{x2},{by + bh + 4}" fill="none" stroke="{MUTED}" '
            f'stroke-width="1.4" stroke-dasharray="2,3" marker-end="url(#arrow)"/>')


def svg():
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">',
           f'<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
           f'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="{MUTED}"/></marker></defs>',
           f'<rect width="{W}" height="{H}" fill="#FFFFFF"/>',
           text(W / 2, 32, "Job Allocation System in context — where each design pattern is applied", 22, "bold",
                BLUE),
           text(W / 2, 56, "Numbered badges = patterns in the order the slides discuss them. "
                           "In the SVG and the HTML deck, click a badge to open its slide.", 14, fill=MUTED)]
    # Users and external systems
    users = [("Supervisor", 420, "allocates jobs"), ("Viewer", 640, "reads the board")]
    for name, x, role in users:
        out.append(f'<g stroke="{BLUE}" stroke-width="2" fill="none"><circle cx="{x}" cy="86" r="10"/>'
                   f'<path d="M{x},96 L{x},122 M{x - 14},106 L{x + 14},106 M{x},122 L{x - 11},140 '
                   f'M{x},122 L{x + 11},140"/></g>')
        out.append(text(x + 70, 100, name, 15, "bold"))
        out.append(text(x + 70, 118, role, 12, fill=MUTED))
    out.append(f'<rect x="860" y="80" width="220" height="56" rx="6" fill="#EEF2F7" stroke="{BLUE}"/>')
    out.append(text(970, 104, "Integration client", 15, "bold"))
    out.append(text(970, 122, "REST + OpenAPI 3.1 contract", 12, fill=MUTED))
    # System boundary
    out.append(f'<rect x="30" y="185" width="{W - 60}" height="670" rx="10" fill="none" stroke="{BLUE}" '
               f'stroke-width="2.5" stroke-dasharray="10,6"/>')
    out.append(text(W - 50, 206, "Job Allocation System (Java 21, one JVM)", 15, "bold", BLUE, "end"))
    out.append(badge(W - 432, 200, 15))
    out.append(link(15, text(W - 452, 205, "15 · Layers, Ports & Adapters", 12, "bold", ORANGE, "end")))
    # Tier bands
    for title, y, h in TIERS.values():
        out.append(f'<rect x="45" y="{y + 15}" width="{W - 90}" height="{h - 20}" rx="8" fill="#F3F6FA" '
                   f'stroke="#C9D3E0"/>')
        out.append(text(W - 60, y + h - 14, title, 14, "bold", BLUE, "end"))
    out.append(f'<rect x="1090" y="655" width="290" height="140" rx="8" fill="#FFFFFF" stroke="#C9D3E0" '
               f'stroke-dasharray="4,3"/>')
    out.append(text(1105, 674, "adapter (wired by App)", 12, "bold", MUTED, "start"))
    # Flows first, so boxes are drawn on top
    out += [arrow(a, b) for a, b in FLOWS] + [arrow(a, b, True) for a, b in DOWN]
    out += [under(a, b, d) for a, b, d in UNDER]
    for x in (420, 640, 970):
        out.append(f'<path d="M{x},{150 if x != 970 else 136} L{160},246" fill="none" stroke="{MUTED}" '
                   f'stroke-width="1.4" stroke-dasharray="5,4" marker-end="url(#arrow)"/>')
    out.append(text(300, 175, "HTTP :8080 (pages, /api/v1)", 12, fill=MUTED, anchor="start"))
    # Components with their badges and captions
    for x, y, w, h, lines, badges in COMPONENTS.values():
        out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="#FFFFFF" stroke="{BLUE}" '
                   f'stroke-width="1.4"/>')
        first = y + h / 2 + (5 if len(lines) == 1 else -4)
        for i, line in enumerate(lines):
            out.append(text(x + w / 2, first + i * 18, line, 13 if i == 0 else 12,
                            "bold" if i == 0 else "normal", INK if i == 0 else MUTED))
        for i, (n, caption) in enumerate(badges):
            bx = x + w - 6 - i * 30
            out.append(badge(bx, y - 2, n))
            out.append(link(n, text(x + 4, y + h + 18 + i * 16, f"{n} · {caption}", 12, "bold", ORANGE, "start")))
    # Database
    out.append(f'<path d="M935,736 L935,880" stroke="{MUTED}" stroke-width="1.6" marker-end="url(#arrow)"/>')
    out.append(text(945, 868, "JDBC", 12, fill=MUTED, anchor="start"))
    out.append(f'<ellipse cx="935" cy="890" rx="80" ry="10" fill="{ORANGE_BG}" stroke="{ORANGE}"/>'
               f'<rect x="855" y="890" width="160" height="56" fill="{ORANGE_BG}" stroke="none"/>'
               f'<path d="M855,890 L855,946 M1015,890 L1015,946" stroke="{ORANGE}"/>'
               f'<ellipse cx="935" cy="946" rx="80" ry="10" fill="{ORANGE_BG}" stroke="{ORANGE}"/>')
    out.append(text(935, 914, "PostgreSQL 16", 13, "bold"))
    out.append(text(935, 930, "or MySQL 8.4 (Docker)", 12))
    # Legend: every pattern, linked to its slide
    out.append(text(40, 885, "Patterns → slide", 14, "bold", BLUE, "start"))
    for i, (n, (name, slide)) in enumerate(PATTERNS.items()):
        col, row = divmod(i, 8)
        x, y = 40 + col * 400, 910 + row * 18
        if col == 1:
            x = 1060
        if col == 0 and row >= 8:
            continue
        out.append(link(n, text(x, y, f"{n}. {name} — slide {slide}", 12, fill=INK, anchor="start")))
    out.append("</svg>")
    return "\n".join(out)


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "24-pattern-context.svg"
    with open(target, "w", encoding="utf-8") as f:
        f.write(svg())
    print("Wrote", target)
