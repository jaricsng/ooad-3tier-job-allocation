# Build all teaching outputs from the Markdown and PlantUML sources.
# Sources of truth:  slides/*.md  docs/*.md  diagrams/src/*.puml
# Generated outputs: diagrams/png, diagrams/svg, build/

SLIDES   := slides/ooad-3tier-patterns.md
DOC      := docs/ooad-walkthrough.md
BUILD    := build
MARP     := npx --yes @marp-team/marp-cli@latest --allow-local-files
PLANTUML ?= plantuml
# Large diagrams (03, 04, 05, 22) exceed PlantUML's default 4096 px limit and would be cut off.
export PLANTUML_LIMIT_SIZE := 16384

.PHONY: all diagrams slides slides-pdf slides-pptx slides-html slides-with-notes doc-pdf check-public clean

all: diagrams slides doc-pdf

diagrams:
	cd diagrams/src && $(PLANTUML) -Sdpi=180 -tpng -o ../png *.puml
	cd diagrams/src && $(PLANTUML) -tsvg -o ../svg *.puml
	cd diagrams/src && python3 24-pattern-context.py ../svg/24-pattern-context.svg
	@command -v rsvg-convert >/dev/null && rsvg-convert -w 2400 diagrams/svg/24-pattern-context.svg -o diagrams/png/24-pattern-context.png \
	  || echo "rsvg-convert not found (brew install librsvg): kept the committed 24-pattern-context.png"

slides: slides-pdf slides-pptx slides-html

slides-pdf: | $(BUILD)
	$(MARP) $(SLIDES) --pdf -o $(BUILD)/ooad-3tier-patterns.pdf

slides-pptx: | $(BUILD)
	$(MARP) $(SLIDES) --pptx -o $(BUILD)/ooad-3tier-patterns.pptx

slides-html: | $(BUILD)
	$(MARP) $(SLIDES) --html -o $(BUILD)/ooad-3tier-patterns.html

# Your own copy of the deck with the private speaker notes merged back in (needs private/).
slides-with-notes: | $(BUILD)
	python3 private/notes.py merge
	$(MARP) build/with-notes/ooad-3tier-patterns.md --pptx -o $(BUILD)/ooad-3tier-patterns-notes.pptx
	$(MARP) build/with-notes/ooad-3tier-patterns.md --html -o $(BUILD)/ooad-3tier-patterns-notes.html

# Gate before every commit and push: no private teaching material in committed files.
check-public:
	python3 scripts/check_public.py

doc-pdf: | $(BUILD)
	cd docs && pandoc ooad-walkthrough.md -o ../$(BUILD)/ooad-walkthrough.pdf \
	  --pdf-engine=xelatex -V geometry:margin=2cm -V mainfont="Helvetica" --toc

$(BUILD):
	mkdir -p $(BUILD)

clean:
	rm -rf $(BUILD)
