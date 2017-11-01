
#====== U S E R - S E T T I N G S =========;;

# Source code 
src         := $(wildcard src/*.scala)

# Application name without file extension 
app         := jmhttp

# Installation directory 
installdir  := ~/bin

# ===== S E T T I N G S =====================;;

appjar := bin/$(app).jar


# ===== R U L E S ========================== ;;

all: $(appjar)

force: $(src)
	scalac $(src) -d $(appjar)

$(appjar): $(src)
	fsc $(src) -d $(appjar)

# Make executable uber-jar not shrunk.
sh: $(appjar)
	jarget uber -scala -sh -m $(appjar) -o bin/jmhttp

# Make executable uber-jar and shrink it with proguard.
sh-guard: $(appjar) config.pro
	mkdir -p bin
	jarget uber -scala -m $(appjar) -o bin/jmhttp-uber.jar
	@# Shrink app with proguard 
	java -jar proguard.jar @config.pro
	@# Make file executable 
	java -jar ~/bin/jarget uber -exjar bin/$(app)-pro.jar bin/$(app)
	@# Remove temporary files
	rm -rf bin/$(app)-uber.jar bin/$(app)-pro.jar

doc: $(src)
	scaladoc $(src) -doc-title "jmHttp Server - Scala Micro Http Server" -doc-version "1.0" -doc-source-url "https://github.com/caiorss/jmhttp" -d ./bin/docs


test: $(appjar)
	scala $(appjar) --dirlist image:/home/archbox/Pictures music:/home/archbox/Music wiki:/home/archbox/Documents/wiki


# Requires the rule $ make sh or $ make sh-guard to be run before.
install:
	@echo "Installing application "
	cp -v bin/jmhttp $(installdir)

uninstall:
	@echo "Uninstalling application"
	rm -v  $(installdir)/$(app)
