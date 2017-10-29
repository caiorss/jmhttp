
#====== U S E R - S E T T I N G S =========;;

src  := main.scala 
app  := mhttp

# ===== S E T T I N G S =====================;;

appjar := $(app).jar


# ===== R U L E S ========================== ;;

all: $(appjar)


$(appjar): $(src)
	scalac $(src) -d $(appjar)

uber: $(appjar)
	jarget uber -scala -sh -m $(appjar) -o mtthp

