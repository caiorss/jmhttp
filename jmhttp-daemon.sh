#!/usr/bin/env sh
#
# Linux/Unix Daemon wrapper for jmhttp server. 
#
# Reference doc:
# - https://unix.stackexchange.com/questions/147916/getting-the-process-id-out-of-command-launched-with-su-c
# - https://stackoverflow.com/questions/534648/how-to-daemonize-a-java-program
# - https://stackoverflow.com/questions/11203483/run-a-java-application-as-a-service-on-linux
#

# Uncomment the line below to debug this script.
##
#set -x 

#------- USER CONFIGURATION ---------------------------#

# Location of jmhttp jar file. 
programLocation=bin/jmhttp

# Directory where the log file will be stored
mkdir -p ~/logs 
logdir=~/logs  

#--------- DO NOT CHANGE THE CODE BELOW ---------------#
progname=jmhttp
logfile=$logdir/$progname.log
pidfile=/tmp/jmhttp.pid


start(){
    if [ ! -f $pidfile ]; then
        program_args="${@:2}"
        echo "Starting JmHttp Server with parameters: $program_args"
        # Run as daemon and save pid
        nohup java -jar $programLocation $program_args > $logfile 2>&1 &
        echo $! > $pidfile
        exit 0 
    else
        echo "Jmhttp server is already running."
        exit 1 
    fi    
}

stop(){
    if [ -f $pidfile ]; then
        kill $(cat $pidfile)
        rm  $pidfile
        echo "Jmhttp server stopped."
        exit 0 
    else
        echo "Jmhttp server is not running."
        exit 1
    fi    
}

case $1 in

    start)
        start "${@:1}"
        ;;
    stop)
        stop
        ;;
    status)
        if ps -p $(cat $pidfile) > /dev/null ; then
            echo "Server is running."
            tail -f $logfile
            exit 0
        else
            echo "Jmhttp server is not running"
            exit 1
        fi
        ;;
    *)
        this=$(basename $0)
        cat <<EOF
 Jmhttp Unix Daemon wrapper. 

Usage: ./$this [option] [server-options] ... 

 + Start server daemon with command line options.
    \$ $this start [server-options]

    Examples:                              
    \$ $this start ~/Docs                 
      * Equivalence: jmhttp ~/Docs

    \$ $this start /dir --auth=user:pass  
      * Equivalence: jmhttp /dir --auth=user:pass

 + Stop server daemon 
   \$ $this stop 

 + Check server status and log
   \$ $this status 
EOF

        exit 1;
esac
