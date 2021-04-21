#!/usr/bin/env bash
if type -p java > /dev/null; then
    _java=java
elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
    _java="$JAVA_HOME/bin/java"
else
    printf "no java found. DahuDEFServer requires java 1.8 or greater to run\n"
fi
if [ -e DEFStop.sh ]
then
    printf "Are you sure that DahuDEFServer isn't running already? (found a 'DEFStop.sh' )\n"
elif [[ "$_java" ]]; then
    version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    if [[ "$version" > "1.8" ]]; then
        DEF_JVM=/usr/bin/java

        if [ "$(uname)" == "Darwin" ]; then
          DEF_JVM_OPTS="-Djava.library.path=./lib/native/mac-x86_64 -Dorg.apache.activemq.SERIALIZABLE_PACKAGES=* -jar ./lib/DahuDEFServer.jar -cdir ./config -c DEFConfig_Processor_QBE.json"
        else
          DEF_JVM_OPTS="-Djava.library.path=./lib/native/linux-x86_64 -Dorg.apache.activemq.SERIALIZABLE_PACKAGES=* -jar ./lib/DahuDEFServer.jar -cdir ./config -c DEFConfig_Processor_QBE.json"
          export LD_LIBRARY_PATH=./lib/native/linux-x86_64
        fi
        DEF_PID=DEFServer
        DEF_LOG=./logs/main.log
        CURDIR=$PWD
        cd ../
        nohup $DEF_JVM $DEF_JVM_OPTS 2>&1 >>$DEF_LOG &
        pid=$!
        cd "$CURDIR"
        printf "Starting DahuDEFServer...\n"
        sleep 3
        ps -p $pid > /dev/null
        if [ $? -eq 0 ]; then

            printf "#!/usr/bin/env bash\npid=$pid\nps -p \$pid > /dev/null\nif [ \$? -eq 1 ]; then\nprintf \"DahuDEFServer is not currently running (with the pid \$pid)\\n\\n\"\nelse\nprintf \"Stopping DahuDEFServer...\\n\"\nkill \$pid\nsleep 6\nps -p $pid > /dev/null\nif [ \$? -eq 0 ]; then\nprintf \"...DahuDEFServer failed to stop in a timely manner.\\n\\n\"\nelse\nprintf \"...DahuDEFServer stopped\\n\\n\"\nrm -- "DEFStop.sh"\nfi\nfi\n" > DEFStop.sh

            chmod +x DEFStop.sh

            printf "...DahuDEFServer started. Use ./DEFStop.sh to terminate\n\n"
        else
            printf "...DahuDEFServer failed to start. \n   Are you sure its not already running? (use ./DEFStop.sh to stop)\n\n"
        fi

    else
        printf "Java version is less than 1.8. DahuDEFServer requires version 1.8 or greater."
    fi
fi
