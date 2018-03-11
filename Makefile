
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
uber: bin/jmhttp-uber.jar

force: $(src)
	jarget exec javax.jmdns/jmdns/3.4.1 -- scalac $(src) -d $(appjar)

$(appjar): $(src)
	jarget exec javax.jmdns/jmdns/3.4.1 -- fsc $(src) -d $(appjar)

# Make executable uber-jar not shrunk.
sh: $(appjar)
	jarget uber -scala -exe uexe -m $(appjar) -o bin/jmhttp  -p javax.jmdns/jmdns/3.4.1


# Make executable uber-jar and shrink it with proguard.
sh-guard: $(appjar) config.pro
	mkdir -p bin
	jarget uber -o bin/jmhttp-uber.jar -scala -m $(appjar) -p javax.jmdns/jmdns/3.4.1
	@# Shrink app with proguard 
	java -jar proguard.jar @config.pro
	@# Make file executable 
	java -jar ~/bin/jarget uber -exjar bin/$(app)-pro.jar bin/$(app)
	@# Remove temporary files
	rm -rf bin/$(app)-uber.jar bin/$(app)-pro.jar
	@# Generate sha256 hash for application data integrity checking.
	cd bin && sha256sum $(app) > $(app).sha256

doc: $(src)
	scaladoc $(src) -doc-title "jmHttp Server - Scala Micro Http Server" -doc-version "1.0" -doc-source-url "https://github.com/caiorss/jmhttp" -d ./bin/docs


test1: $(appjar)
	jarget exec javax.jmdns/jmdns/3.4.1 -- scala $(appjar) --loglevel=INFO -p=9090 /home/archbox/Pictures

test2: $(appjar)
	jarget exec javax.jmdns/jmdns/3.4.1 -- scala $(appjar) --zeroconf --loglevel=ALL --multiple -p=8000 image:/home/archbox/Pictures music:/home/archbox/Music wiki:/home/archbox/Documents/wiki


# Requires the rule $ make sh or $ make sh-guard to be run before.
install:
	@echo "Installing application "
	cp -v bin/jmhttp $(installdir)

uninstall:
	@echo "Uninstalling application"
	rm -v  $(installdir)/$(app)
