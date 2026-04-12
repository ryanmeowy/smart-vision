.PHONY: escli-build escli-test escli-run-help escli-clean

ESCLI_DIR := cli/escli
MVN := /opt/homebrew/bin/mvn

escli-build:
	cd $(ESCLI_DIR) && $(MVN) -q test package

escli-test:
	cd $(ESCLI_DIR) && $(MVN) -q test

escli-run-help: escli-build
	cd $(ESCLI_DIR) && java -jar target/sv-es-cli.jar --help

escli-clean:
	rm -rf $(ESCLI_DIR)/target
