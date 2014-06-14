.PHONY: all
all: clean compile xtl 

.PHONY: compile
compile:	
	mkdir -p build
	scalac -cp ".:lib/tagsoup-1.2.1.jar:lib/:lib/sqlite-jdbc-3.7.2.jar" -d build `find . -name \*.scala`

.PHONY: clean
clean:
	rm -Rf build
	rm -f XTL.jar

.PHONY: xtl
xtl:
	echo "Manifest-Version: 1.0" > jar.manifest
	echo "Name: XTL" >> jar.manifest
	echo "build-Date: " `date` >> jar.manifest
	echo "Main-Class: " im.boddy.plus500.XTL >> jar.manifest
	echo "Class-Path: " lib/*.jar >> jar.manifest
	echo "" >> jar.manifest

	jar -cfm XTL.jar jar.manifest  -C  build .
	rm -f jar.manifest

