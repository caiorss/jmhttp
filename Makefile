
#====== U S E R - S E T T I N G S =========;;

src  := $(wildcard src/*.scala)
app  := jmhttp

# ===== S E T T I N G S =====================;;

appjar := $(app).jar


# ===== R U L E S ========================== ;;

all: $(appjar)


$(appjar): $(src)
	scalac $(src) -d $(appjar)

uber: $(appjar)
	jarget uber -scala -sh -m $(appjar) -o mtthp

doc: $(src)
	scaladoc $(src) -doc-title "Mtthp Server - Scala Micrio" -doc-version "1.0" -doc-source-url "https://github.com/caiorss/jarget" -d ./bin/docs


test: $(appjar)
	scala $(appjar) images:/home/archbox/Pictures files:/home/archbox/Downloads 
