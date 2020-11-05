SHELL = bash

.EXPORT_ALL_VARIABLES:
.PHONY: test build

repl:
	rm -rf .cpcache/ && rm -rf ui/.cpcache/ && DEBUG=true && cd ui && clojure -A:dev:test:nrepl

build:
	clojure -A:build
	mv target/app-1.0.0-SNAPSHOT-standalone.jar app.jar

run-jar:
	java -jar app.jar

test:
	clojure -A:test:runner
