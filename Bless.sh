#!/bin/bash

# Create blessed directory
rm -rf build/blessed
mkdir -p build/blessed

# Get all files under VCS
ALLFILES=`git ls-tree --name-only -r HEAD`

# Get the last annotated tag
TAGFROM=`git describe --abbrev=0`

# For each file, obtain the blame from the tag
echo "$ALLFILES" | while read f
do
	# Detect whether git thinks the file is binary
	CHG=`git diff 4b825dc642cb6eb9a060e54bf8d69288fbee4904 --numstat HEAD -- "$f" | cut -f1`
	[ "$CHG" == "-" ] && echo "Skipping '$f'..." && continue
	echo "Blessing '$f'..."
	# Gather the involved revisions
	REVS=`git blame -b -s -w $TAGFROM.. -- "$f" | cut -d' ' -f1 | sort | uniq`
	# Gather revision logs
	COMMITS=`echo "$REVS" | while read r; do [ ! -z "$r" ] && git log --oneline --pretty=tformat:"[%h] %cd (%cn) %s" --date=short -n 1 $r; done | sort -r -k 2`
	# Generate real blame file
	mkdir -p "build/blessed/`dirname "$f"`"
	git blame -b -s -w $TAGFROM.. -- "$f" > "build/blessed/$f"

	# Optionally produce compilable source code for certain languages that supports block comments
	case "${f##*.}" in
	java|c|cpp|h|hpp)
		sed -i -e 's/^\([^)]\+)\)/\/* \1 *\//' "build/blessed/$f"
		[ ! -z "$COMMITS" ] && ( echo && echo "---- Commit Logs ----" && echo "$COMMITS" ) >> "build/blessed/$f"
                sed -i -e 's/^\([^/]\)/\/\/ \1/' "build/blessed/$f"
		sed -i -e 's/^\/\(\*[^*]\+\*\)\/\(\s\+\)\*/ \1 \2*/' "build/blessed/$f"
	;;
	*)
		[ ! -z "$COMMITS" ] && ( echo && echo "---- Commit Logs ----" && echo "$COMMITS" ) >> "build/blessed/$f"
		mv "build/blessed/$f" "build/blessed/$f.bless"
	;;
	esac
	unix2dos "build/blessed/$f" 2>/dev/null
done

# Prepare a compress package containing the blames
echo Generating blessed source package
( cd build/blessed && tar -cz --xform s:'./':: -f ../Source.Blessed.tgz . )

