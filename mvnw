#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to You under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#   https://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_HOME" ]; then
    MAVEN_USER_HOME="${HOME}/.m2/wrapper"
else
    MAVEN_USER_HOME="$MAVEN_HOME"
fi

PROPERTIES_FILE=".mvn/wrapper/maven-wrapper.properties"
DISTRIBUTION_URL=$(grep "^distributionUrl=" "$PROPERTIES_FILE" | sed 's/distributionUrl=//')
DISTRIBUTION_FILE=$(basename "$DISTRIBUTION_URL")
# The apache-maven-X.Y.Z-bin.zip archive extracts to a folder named apache-maven-X.Y.Z
# (without the "-bin" suffix), so strip "-bin.zip" to match the extracted directory.
DISTRIBUTION_NAME="${DISTRIBUTION_FILE%-bin.zip}"
MAVEN_HOME="$MAVEN_USER_HOME/dists/$DISTRIBUTION_NAME"

if [ ! -d "$MAVEN_HOME" ]; then
    mkdir -p "$MAVEN_HOME"
    echo "Downloading $DISTRIBUTION_URL"
    if command -v curl > /dev/null 2>&1; then
        curl -fsSL "$DISTRIBUTION_URL" -o "/tmp/$DISTRIBUTION_FILE"
    elif command -v wget > /dev/null 2>&1; then
        wget -q "$DISTRIBUTION_URL" -O "/tmp/$DISTRIBUTION_FILE"
    else
        echo "ERROR: Neither curl nor wget found. Please install one." >&2
        exit 1
    fi
    unzip -q "/tmp/$DISTRIBUTION_FILE" -d "$MAVEN_USER_HOME/dists/"
    rm -f "/tmp/$DISTRIBUTION_FILE"
fi

MAVEN_EXECUTABLE="$MAVEN_HOME/bin/mvn"
if [ ! -f "$MAVEN_EXECUTABLE" ]; then
    echo "ERROR: Maven executable not found at $MAVEN_EXECUTABLE" >&2
    exit 1
fi

exec "$MAVEN_EXECUTABLE" "$@"
