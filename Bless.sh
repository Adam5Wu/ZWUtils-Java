#!/bin/bash

# Constants
REVZERO="4b825dc642cb6eb9a060e54bf8d69288fbee4904"
BUILDDIR="build"
BLESSDIR="${BUILDDIR}/blessed"
BLESSCFG=".bless.config"
BLESSPKG="Source.Blessed"

# Environmental sanity check
[ ${BASH_VERSION%%.*} -lt 4 ] && echo "Require bash version 4 and up!" && exit 1
TOOLS=( git sed cut unix2dos tar )
for t in ${TOOLS[@]}; do
	echo -n "Checking '$t'... " && which "$t" || { echo "not found!"; exit 2; }
done
echo -n "Checking annotated tags... "
git describe 2>/dev/null || {
	echo "none detected!";
	echo -n "Checking any tag... "
	git describe --tags 2>/dev/null || {
		echo "none detected!";
		echo "ERROR: Please tag you repository before using this tool!";
		exit 3
	}
	echo "HINT: Use annotated tags to maximize the benefit of using this tool!";
}

# Pull parameters
UTX=
[ $# -ge 1 ] && UTX="$1"
TAGFROM=
[ $# -ge 2 ] && TAGFROM="$2"
SORTFIELD=2r
[ $# -ge 3 ] && SORTFIELD="$3"

# Parameter sanity check
if [ -z "$TAGFROM" ]; then
	TAGFROM=`git describe --abbrev=0 2>/dev/null`
	[ -z "$TAGFROM" ] && TAGFROM="$REVZERO" || {
		TAGCUR=`git describe`
		[ "$TAGFROM" == "$TAGCUR" ] && TAGFROM=`git describe --abbrev=0 ${TAGFROM}^ 2>/dev/null`
		[ -z "$TAGFROM" ] && TAGFROM="$REVZERO"
	}
else
	TAGFROM=`git describe --tags ${TAGFROM} 2>/dev/null`
	[ -z "$TAGFROM" ] && { echo "ERROR: Invalid origin tag '${TAGFROM}'"; exit 4; }
fi
[ "$TAGFROM" == "$REVZERO" ] && echo "Blessing from the very beginning..." || echo "Blessing from revision '${TAGFROM}'..."

FLUSH=0
# Load previous profile
[ -f "${BUILDDIR}/${BLESSCFG}" ] && . ${BUILDDIR}/${BLESSCFG} || FLUSH=X

[ "$_UTX" != "$UTX" ] && FLUSH=1
[ "$_TAGFROM" != "$TAGFROM" ] && FLUSH=2
[ "$_SORTFIELD" != "$SORTFIELD" ] && FLUSH=3

# Check if previous generated files need to be flushed
[ "$FLUSH" != "0" ] && rm -rf ${BLESSDIR} && ( echo "_UTX=\"$UTX\"" ; echo "_SORTFIELD=\"$SORTFIELD\"" ; echo "_TAGFROM=\"$TAGFROM\"" ) > ${BUILDDIR}/${BLESSCFG}

# Create blessed directory (if not exists)
mkdir -p ${BLESSDIR}

# Get all files under VCS
readarray -t ALLFILES < <( git ls-tree --name-only -r HEAD )

declare -A BLESSED
CHANGED=()
# For each file, generate the blessed version
for (( x=0; x<${#ALLFILES[@]}; x++ )); do
	f="${ALLFILES[$x]}"

	# Skip if blessed file exists and is newer than original
	fbless=
	case "${f##*.}" in
	java|c|cpp|h|hpp)
		fbless="${BLESSDIR}/$f"
	;;
	*)
		fbless="${BLESSDIR}/$f.blessed"
	;;
	esac
	[ -f "$fbless" -a "$fbless" -nt "$f" ] && {
		BLESSED["$fbless"]=0
		continue
	}

	# Detect whether git thinks the file is binary
	CHG=`git diff 4b825dc642cb6eb9a060e54bf8d69288fbee4904 --numstat HEAD -- "$f" | cut -f1`
	[ "$CHG" == "-" ] && echo "Skipping binary '$f'..." && continue

	CHANGED+=("$f")
	BLESSED["$fbless"]=1
	echo "Blessing '$f'..."
	# Gather the involved revisions
	REVS=( `git blame -b -s -w $TAGFROM.. -- "$f" | cut -d' ' -f1 | sort | uniq` )
	# Gather revision logs
	COMMITS=`for r in ${REVS[@]}; do [ ! -z "$r" ] && echo -n "[$r] " && git log --oneline --pretty=tformat:"%cd (%cn)%x09%s" --date=short -n 1 "$r"; done`
	# Generate real blame file
	mkdir -p "${BLESSDIR}/`dirname "$f"`"
	git blame -b -s -w $TAGFROM.. -- "$f" > "$fbless"

	# Uniform tagging handling
	if [ ! -z "$UTX" ]; then
		readarray -t TAGS < <( echo "$COMMITS" | cut -f2- )
		UTXLEN=${#UTX}
		MAXLEN=0
		for (( i=0; i<${#TAGS[@]}; i++ )); do
			TAG=`echo "${TAGS[$i]}" | cut -d' ' -f1`
			[ "${TAG:0:$UTXLEN}" != "$UTX" ] && echo "WARNING: Untagged message '${TAGS[$i]}'" && TAG="$UTX?"
			LEN=${#TAG}
			[ $LEN -gt $MAXLEN ] && MAXLEN=$LEN
			TAGS[$i]=$TAG
		done
		for (( i=0; i<${#TAGS[@]}; i++ )); do
			TAGS[$i]=`printf "%-${MAXLEN}s" ${TAGS[$i]}`
		done
		for (( i=0; i<${#TAGS[@]}; i++ )); do
			sed -i -e "s/^${REVS[$i]}/${REVS[$i]} ${TAGS[$i]}/" "$fbless"
		done
		sed -i -e "s/^ /  `printf "%${MAXLEN}s" ''`/" "$fbless"
	fi

	# Sort comment logs
	COMMITS=`echo "$COMMITS" | sort -k $SORTFIELD`
	# Optionally produce compilable source code for certain languages that supports block comments
	case "${f##*.}" in
	java|c|cpp|h|hpp)
		sed -i -e 's/^\([^)]\+)\)/\/* \1 *\//' "$fbless"
		[ ! -z "$COMMITS" ] && ( echo && echo "---- Commit Logs ----" && echo "$COMMITS" ) >> "$fbless"
                sed -i -e 's/^\([^/]\)/\/\/ \1/' "$fbless"
		sed -i -e 's/^\/\(\*[^*]\+\*\)\/\(\s\+\)\*/ \1 \2*/' "$fbless"
	;;
	*)
		[ ! -z "$COMMITS" ] && ( echo && echo "---- Commit Logs ----" && echo "$COMMITS" ) >> "$fbless"
	;;
	esac

	# Covert to DOS new line style to maximumize audiences
	unix2dos "$fbless" 2>/dev/null
done

# Remove blessed version of any stale files
readarray -t BLSFILES < <( find "${BLESSDIR}" -type f )
for (( x=0; x<${#BLSFILES[@]}; x++ )); do
	f="${BLSFILES[$x]}"

	[ -z "${BLESSED[$f]}" ] && {
		CHANGED+=("$f")
		echo "Removing stale bless '$f'..."
		rm -f "$f"
	}
done

# Prepare a compress package containing the blames
[ ${#CHANGED[@]} -ne 0 ] && {
	echo "Generating blessed source package..."
	( cd ${BLESSDIR} && tar -cz --xform s:'./':: -f ../${BLESSPKG}.tgz . )
} || echo "No change from previous bless"

