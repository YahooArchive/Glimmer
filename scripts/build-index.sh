#!/bin/sh
#
# Copyright (c) 2012 Yahoo! Inc. All rights reserved.
# 
#  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
#  Unless required by applicable law or agreed to in writing, software distributed under the License is 
#  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and limitations under the License.
#  See accompanying LICENSE file.
#

if [ "$#" -le 2 ] ; then
	echo Usage: "${0} <tuple file on local disk or HDFS> <build name> [tuple filter xml file] [no. sub indices]"
	exit 1
fi
INPUT_ARG=${1}
BUILD_NAME=${2}

# Optionally set PrepTool's tuple filter definition file.  This is a file containing an XStream serialized instance of a TupleFilter.
# See TestTupleFilter.xml or SchemaDotOrgRegexTupleFilter.xml as examples and http://xstream.codehaus.org/converters.html.
PREP_FILTER_FILE=""
if [ ! -z ${3} -a "${3}" != "-" ] ; then
	PREP_FILTER_FILE=${3}
fi

# Optionally set the number of reducers to use when generating indexes.
SUBINDICES=20
if [ ! -z ${4} ] ; then
	if ! [[ "${4}" =~ ^[0-9]+$ ]] ; then
		echo "Number of sub indices is not numeric: " ${4}
		exit 1
	fi		
	SUBINDICES=${4}
fi

# The ontology file to pass to PrepTool and TripleIndexGenerator
ONTOLOGY="schemaDotOrg.owl"

# Set to "-C" to exclude context from processing. 
EXCLUDE_CONTEXTS=""

# Number of predicates to use when building vertical indexes.  
# The occurrences of predicates found in the source tuples are counted and then sorted by occurrence count.
# This limits the resulting list to the top N predicates.
N_VERTICAL_PREDICATES=200

# To allow the use of commons-configuration version 1.8 over Hadoop's version 1.6 we export HADOOP_USER_CLASSPATH_FIRST=true
# See https://issues.apache.org/jira/browse/MAPREDUCE-1938 and http://hadoop.apache.org/common/docs/r0.20.204.0/releasenotes.html
# This is for hadoop 0.20.xx
#export HADOOP_USER_CLASSPATH_FIRST=true

#HADOOP_NAME_NODE="localhost:9000"
HADOOP_NAME_NODE=""
DFS_ROOT_DIR="hdfs://${HADOOP_NAME_NODE}"
DFS_USER_DIR="${DFS_ROOT_DIR}/user/${USER}"
DFS_BUILD_DIR="${DFS_USER_DIR}/index-${BUILD_NAME}"
LOCAL_BUILD_DIR="${HOME}/tmp/index-${BUILD_NAME}"

QUEUE=${QUEUE:-default}

echo
echo "Using ${INPUT_ARG} as input and ${DFS_BUILD_DIR} as the distributed build dir.."
if [ ! -z ${PREP_FILTER_FILE} ] ; then
    echo "filtering by ${PREP_FILTER_FILE}.."
fi
echo "reducing into ${SUBINDICES} sub indices.."
echo "and writing output to local disk in ${LOCAL_BUILD_DIR}."
echo

JAR_FOR_HADOOP="../target/Glimmer-0.0.1-SNAPSHOT-jar-for-hadoop.jar"

COMPRESSION_CODEC="org.apache.hadoop.io.compress.BZip2Codec"
COMPRESSION_CODECS="org.apache.hadoop.io.compress.DefaultCodec,org.apache.hadoop.io.compress.GzipCodec,${COMPRESSION_CODEC}"

HASH_EXTENSION=".smap"

INDEX_FILE_EXTENSIONS="counts countsoffsets frequencies occurrencies pointers pointersoffsets positions positionsoffsets properties sumsmaxpos terms"

if [ ! -f ${JAR_FOR_HADOOP} ] ; then
	echo "Projects jar file missing!! ${JAR_FOR_HADOOP}"
	exit 1
fi

HADOOP_CMD=`which hadoop`
if [ -z ${HADOOP_CMD} ] ; then
	echo "Can't find the hadoop command."
	exit 1
fi

BZCAT_CMD=`which bzcat`
if [ -z ${BZCAT_CMD} ] ; then
	echo "Can't find the bzcat command."
	exit 1
fi

${HADOOP_CMD} dfs -test -d ${DFS_BUILD_DIR}
if [ $? -ne 0 ] ; then
	echo "Creating DFS build directory ${DFS_BUILD_DIR}.."
	${HADOOP_CMD} dfs -mkdir ${DFS_BUILD_DIR}
	if [ $? -ne 0 ] ; then
		echo "Failed to create build directory ${DFS_BUILD_DIR} in DFS."
		exit 1
	fi
else
	read -p "Build dir ${DFS_BUILD_DIR} already exists in DFS. Continue anyway? (Y)" -n 1 -r
	echo
	if [[ ! $REPLY =~ ^[Yy]$ ]] ; then
		exit 1
	fi
fi

if [ ! -d ${LOCAL_BUILD_DIR} ] ; then
	echo "Creating local build directory ${LOCAL_BUILD_DIR}.."
	mkdir ${LOCAL_BUILD_DIR}
	if [ $? -ne 0 ] ; then
		echo "Failed to create local build directory ${LOCAL_BUILD_DIR}."
		exit 1
	fi
else
	read -p "Local build dir ${LOCAL_BUILD_DIR} already exists. Continue anyway? (Y)" -n 1 -r
	echo
	if [[ ! $REPLY =~ ^[Yy]$ ]] ; then
		exit 1
	fi
fi

# Is INPUT_ARG a local or in HDFS file?
IN_FILE=unset
if [[ ${INPUT_ARG} == hdfs:* ]] ; then
	${HADOOP_CMD} fs -test -e "${INPUT_ARG}"
	if [ $? -ne 0 ] ; then
		echo "Can't find file ${INPUT_ARG} on cluster!"
		exit 1
	fi
	IN_FILE=${INPUT_ARG}
	echo Using file ${IN_FILE} on cluster as input..
elif [ -f "${INPUT_ARG}" ] ; then
	echo "Uploading local file ${INPUT_ARG} to cluster.."
	IN_FILE="${DFS_BUILD_DIR}"/$(basename "${INPUT_ARG}")
	${HADOOP_CMD} fs -test -e "${IN_FILE}"
	if [ $? -eq 0 ] ; then
		read -p "File ${INPUT_ARG} already exists on cluster as ${IN_FILE}. Overwrite, Continue(using file on cluster) or otherwise quit? (O/C)" -n 1 -r
		echo
		if [[ $REPLY =~ ^[Cc]$ ]] ; then
			INPUT_ARG=""
		elif [[ ! $REPLY =~ ^[Oo]$ ]] ; then
			exit 1
		fi
	fi
	
	if [ ! -z ${INPUT_ARG} ] ; then
		${HADOOP_CMD} fs -put "${INPUT_ARG}" "${DFS_BUILD_DIR}"
		if [ $? -ne 0 ] ; then
			echo "Failed to upload input file ${INPUT_ARG} to ${IN_FILE}"
			exit 1
		fi
		echo "Uploaded ${INPUT_ARG} to ${IN_FILE}"
	fi	
else
	echo "${INPUT_ARG} not found."
	echo "Give either a local file to upload or the full URL of a file on the cluster."
	exit 1
fi

function groupBySubject () {
	local INPUT_FILE=${1}
	local PREP_DIR=${2}
	echo Processing tuples from file ${INPUT_FILE}...
	echo
	
	HADOOP_FILES=""
	if [ ! -z ${PREP_FILTER_FILE} ] ; then
		HADOOP_FILES="-files ${PREP_FILTER_FILE}#FilterXml"
	fi
	ONTOLOGY_OPTION=""
	if [ ! -z ${ONTOLOGY} ] ; then
		ONTOLOGY_OPTION="-O ${ONTOLOGY}"
	fi
	
	local CMD="${HADOOP_CMD} jar ${JAR_FOR_HADOOP} com.yahoo.glimmer.indexing.preprocessor.PrepTool \
		-Dio.compression.codecs=${COMPRESSION_CODECS} \
		-Dmapreduce.map.speculative=true \
		-Dmapred.child.java.opts=-Xmx800m \
		-Dmapreduce.map.memory.mb=2000 \
		-Dmapreduce.reduce.memory.mb=2000 \
		-Dmapreduce.output.fileoutputformat.compress.codec=${COMPRESSION_CODEC} \
		-Dmapreduce.output.fileoutputformat.compress=false \
		-Dmapreduce.job.queuename=${QUEUE} \
		${HADOOP_FILES} \
		${ONTOLOGY_OPTION} \
		${EXCLUDE_CONTEXTS} ${INPUT_FILE} ${PREP_DIR}"
	echo ${CMD}
	${CMD}
		
	local EXIT_CODE=$?
	if [ $EXIT_CODE -ne "0" ] ; then
		echo "PrepTool exited with code $EXIT_CODE. exiting.."
		exit $EXIT_CODE
	fi
}

function moveBySubjectFiles() {
	local PREP_DIR=${1}
	local CMD="${HADOOP_CMD} fs -mv ${PREP_DIR}/part-r-00000/* ${PREP_DIR}"
	echo ${CMD}
	${CMD}
	
	echo "Getting ${N_VERTICAL_PREDICATES} most used predicates in topPredicates."
	${HADOOP_CMD} fs -cat ${PREP_DIR}/predicates | sort -nr | cut -f 2 > ${LOCAL_BUILD_DIR}/allPredicates
	head -${N_VERTICAL_PREDICATES} ${LOCAL_BUILD_DIR}/allPredicates > ${LOCAL_BUILD_DIR}/topPredicates
	${HADOOP_CMD} fs -put ${LOCAL_BUILD_DIR}/topPredicates ${PREP_DIR}
}

function computeHashes () {
	FILES=$@
	echo
	echo Generating Hashes..
	echo "		If you get out of disk space errors you need more space in /tmp for ChunkedHashStore... files"
	echo "		If you get out of heap errors try setting hadoop's HADOOP_HEAPSIZE or HADOOP_CLIENT_OPTS=\"-Xmx3500m\""
	echo
	# Generate Hashes for subjects, predicates and objects and all
	CMD="$HADOOP_CMD jar ${JAR_FOR_HADOOP} com.yahoo.glimmer.util.ComputeHashTool \
		-Dio.compression.codecs=${COMPRESSION_CODECS} \
		-sui ${FILES}"
	echo ${CMD}; ${CMD}
		
	EXIT_CODE=$?
	if [ $EXIT_CODE -ne "0" ] ; then
		echo "Hash generation exited with code $EXIT_CODE. exiting.."
		exit $EXIT_CODE
	fi	
}

function getDocCount () {
    local PREP_DIR=${1}
	# The number of docs is the count of 'all' resources..
	# Note: The number of real docs is actually the number of subjects but as the MG4J docId is taken from the position in the
	# all resources hash MG4J expects that the docId be smaller that the count of all resource(which is greater than the number
	# of real docs)
	# We decided using the all resource count is simpler that using the subject count and hash to get the docIds.  The effect 
	# is that the index contains empty docs and that the Doc count it's accurate. Which may effect scoring in some cases.. 
	NUMBER_OF_DOCS=`${HADOOP_CMD} fs -cat ${PREP_DIR}/all.mapinfo | grep size | cut -f 2`
	if [ -z "${NUMBER_OF_DOCS}" -o $? -ne "0" ] ; then
		echo "Failed to get the number of subjects. exiting.."
		exit 1
	fi
	echo "There are ${NUMBER_OF_DOCS} docs(subjects)."
}

function generateIndex () {
	PREP_DIR=${1}
	METHOD=${2}
	NUMBER_OF_DOCS=${3}
	SUBINDICES=${4}
	METHOD_DIR="${DFS_BUILD_DIR}/${METHOD}"
	
	echo
	echo "RUNING HADOOP INDEX BUILD FOR METHOD:" ${METHOD}
	echo "		When building the vertical indexes a lot of files are created and could possibly exceed your HDFS file count quota."
	echo "		The number of files for the vertical index is roughly equal to:"
	echo "			number of predicates * 11 * number of sub indicies" 
	echo
	
	${HADOOP_CMD} fs -test -e "${METHOD_DIR}"
	if [ $? -eq 0 ] ; then
		read -p "${METHOD_DIR} exists already! Delete and regenerate indexes, Continue using existing indexes or otherwise quit? (D/C)" -n 1 -r
		echo
		if [[ $REPLY =~ ^[Cc]$ ]] ; then
			echo Continuing with existing indexes in ${METHOD_DIR}
			return 0
		elif [[ ! $REPLY =~ ^[Dd]$ ]] ; then
			echo Exiting.
			exit 1
		fi
	
		echo "Deleting DFS indexes in directory ${METHOD_DIR}.."
		${HADOOP_CMD} fs -rmr -skipTrash ${METHOD_DIR}
	fi
	
	HADOOP_FILES=""
	if [ ! -z ${ONTOLOGY} ] ; then
		HADOOP_FILES="-files ${ONTOLOGY}#Ontology"
	fi
	
	echo Generating index..
	local CMD="${HADOOP_CMD} jar ${JAR_FOR_HADOOP} com.yahoo.glimmer.indexing.generator.TripleIndexGenerator \
		-Dio.compression.codecs=${COMPRESSION_CODECS} \
		-Dmapreduce.map.speculative=true \
		-Dmapreduce.job.reduces=${SUBINDICES} \
		-Dmapred.map.child.java.opts=-Xmx3000m \
		-Dmapreduce.map.memory.mb=3000 \
		-Dmapred.reduce.child.java.opts=-Xmx1800m \
		-Dmapreduce.reduce.memory.mb=1800 \
		-Dmapreduce.task.io.sort.mb=128 \
		-Dmapreduce.job.queuename=${QUEUE} \
		-Dmapreduce.job.user.classpath.first=true \
		${HADOOP_FILES} \
		-m ${METHOD} ${EXCLUDE_CONTEXTS} -p ${PREP_DIR}/topPredicates \
		${PREP_DIR}/bySubject.bz2 $NUMBER_OF_DOCS ${METHOD_DIR} ${PREP_DIR}/all.map"
	echo ${CMD}
	${CMD}

	EXIT_CODE=$?
	if [ $EXIT_CODE -ne "0" ] ; then
		echo "TripleIndexGenerator MR job exited with code $EXIT_CODE. exiting.."
		exit $EXIT_CODE
	fi
}

function getSubIndexes () {
	METHOD=${1}
	echo
	echo "COPYING SUB INDEXES TO LOCAL DISK FOR METHOD:" ${METHOD}
	echo
	
	INDEX_DIR="${LOCAL_BUILD_DIR}/${METHOD}"
	if [ -d ${INDEX_DIR} ] ; then
		read -p "${INDEX_DIR} exists already! Overwrite, Continue using existing local files or otherwise quit? (O/C)" -n 1 -r
		echo
		if [[ $REPLY =~ ^[Cc]$ ]] ; then
			return 0
		elif [[ ! $REPLY =~ ^[Oo]$ ]] ; then
			echo ${INDEX_DIR} exists. Exiting..
			exit 1
		fi
		echo Deleting ${INDEX_DIR}
		rm -rf "${INDEX_DIR}"
	fi
	
	mkdir -p ${INDEX_DIR}
	CMD="${HADOOP_CMD} fs -copyToLocal ${DFS_BUILD_DIR}/${METHOD}/part-r-????? ${INDEX_DIR}"
	echo ${CMD}
	${CMD}
	
	EXIT_CODE=$?
	if [ $EXIT_CODE -ne "0" ] ; then
		echo "Failed to copy sub indexes from cluster. Exited with code $EXIT_CODE. exiting.."
		exit $EXIT_CODE
	fi
}

function mergeSubIndexes() {
	METHOD=${1}
	INDEX_DIR="${LOCAL_BUILD_DIR}/${METHOD}"
	echo
	echo "MERGING SUB INDEXES FOR METHOD:" ${METHOD}
	echo
	
	if [ -e "${INDEX_DIR}/*.properties" ] ; then
		read -p "Local .properties files exist in ${INDEX_DIR}! Continue(delete them) or otherwise quit? (C)" -n 1 -r
		echo
		if [[ ! $REPLY =~ ^[Cc]$ ]] ; then
			echo Exiting..
			exit 1
		fi
		echo Deleting old index files from ${INDEX_DIR}...
		for FILE_EXT in ${INDEX_FILE_EXTENSIONS} ; do
			rm -f ${INDEX_DIR}/*.${FILE_EXT}
		done
	fi
	
	# The first reducer write the sizes files for all partitions so we don't need to merge them
	# Move the .sizes files to the correct location before running the merge. Otherwise Merge
	# finds .sizes for only the first partition.
	echo "Moving .sizes files.."
	mv -v ${INDEX_DIR}/part-r-00000/*.sizes ${INDEX_DIR}
	
	PART_DIRS=(`ls -1d ${INDEX_DIR}/part-r-?????`)
	echo "Map Reduce part dirs are:"
	echo ${PART_DIRS[@]}
	echo
	
	INDEX_NAMES=`ls ${PART_DIRS[0]} | awk '/\.properties/{sub(".properties$","") ; print $0}'`
	echo "Index names are:"
	echo ${INDEX_NAMES[@]}
	echo
	
	for INDEX_NAME in ${INDEX_NAMES[@]}; do
		SUB_INDEXES=""
		for PART_DIR in ${PART_DIRS[@]}; do
			SUB_INDEXES="${SUB_INDEXES} ${PART_DIR}/${INDEX_NAME}"
		done
		
		# When merging the alignment index there are no counts.
		NO_COUNTS_OPTIONS=""
		if [ "${INDEX_NAME}" == "alignment" ] ; then
			NO_COUNTS_OPTIONS="-cCOUNTS:NONE -cPOSITIONS:NONE"
		fi
		
		CMD="java -Xmx2G -cp ${JAR_FOR_HADOOP} it.unimi.di.big.mg4j.tool.Merge ${NO_COUNTS_OPTIONS} ${INDEX_DIR}/${INDEX_NAME} ${SUB_INDEXES}"
		echo ${CMD}
		${CMD}
		
		EXIT_CODE=$?
		if [ $EXIT_CODE -ne 0 ] ; then
			echo "Merge of ${METHOD} returned and exit value of $EXIT_CODE. exiting.."
			exit $EXIT_CODE
		fi
		
		echo "Removing part files for index ${INDEX_NAME}"
		for PART_DIR in ${PART_DIRS[@]}; do
			rm ${PART_DIR}/${INDEX_NAME}.*
		done

		CMD="java -Xmx3800m -cp ${JAR_FOR_HADOOP} it.unimi.dsi.big.util.ImmutableExternalPrefixMap \
			-o ${INDEX_DIR}/${INDEX_NAME}.terms \
			${INDEX_DIR}/${INDEX_NAME}.termmap \
			${INDEX_DIR}/${INDEX_NAME}.termmap.dump"
		echo ${CMD}
		${CMD}
		
		EXIT_CODE=$?
		if [ $EXIT_CODE -ne 0 ] ; then
			echo "Creating terms map failed with value of $EXIT_CODE. exiting.."
			exit $EXIT_CODE
		fi
	done	
	rm -rf ${INDEX_DIR}/part-r-?????
}

groupBySubject ${IN_FILE} ${DFS_BUILD_DIR}/prep
moveBySubjectFiles ${DFS_BUILD_DIR}/prep
computeHashes ${DFS_BUILD_DIR}/prep/all

getDocCount ${DFS_BUILD_DIR}/prep

# Horizontal and Vertical index builds could be run in parallel..
generateIndex ${DFS_BUILD_DIR}/prep horizontal ${NUMBER_OF_DOCS} ${SUBINDICES}
getSubIndexes horizontal
mergeSubIndexes horizontal

generateIndex ${DFS_BUILD_DIR}/prep vertical ${NUMBER_OF_DOCS} ${SUBINDICES}
getSubIndexes vertical
mergeSubIndexes vertical

${HADOOP_CMD} fs -copyToLocal "${DFS_BUILD_DIR}/prep/all" "${LOCAL_BUILD_DIR}/all.txt"
${HADOOP_CMD} fs -copyToLocal "${DFS_BUILD_DIR}/prep/all.map" "${LOCAL_BUILD_DIR}"
${HADOOP_CMD} fs -copyToLocal "${DFS_BUILD_DIR}/prep/all.smap" "${LOCAL_BUILD_DIR}"
${HADOOP_CMD} fs -copyToLocal "${DFS_BUILD_DIR}/prep/bySubject.bz2" "${LOCAL_BUILD_DIR}"
${HADOOP_CMD} fs -copyToLocal "${DFS_BUILD_DIR}/prep/bySubject.blockOffsets" "${LOCAL_BUILD_DIR}"

echo Done. Index files are here ${LOCAL_BUILD_DIR}

