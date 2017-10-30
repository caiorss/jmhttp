
#====== U S E R - S E T T I N G S =========;;

src  := $(wildcard src/*.scala)
app  := jmhttp

# ===== S E T T I N G S =====================;;

appjar := bin/$(app).jar


# ===== R U L E S ========================== ;;

all: $(appjar)

force: $(src)
	scalac $(src) -d $(appjar)

$(appjar): $(src)
	fsc $(src) -d $(appjar)

uber: $(appjar)
	jarget uber -scala -sh -m $(appjar) -o bin/jmhttp

doc: $(src)
	scaladoc $(src) -doc-title "jmHttp Server - Scala Micro Http Server" -doc-version "1.0" -doc-source-url "https://github.com/caiorss/jmhttp" -d ./bin/docs


test: $(appjar)
	scala $(appjar) --dirlist image:/home/archbox/Pictures music:/home/archbox/Music wiki:/home/archbox/Documents/wiki
