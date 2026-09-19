#!/bin/sh
# Проект собирается на Java 21 (её же пиннят CI и Dockerfile).
# Lombok из spring-boot 3.2 молча не обрабатывает аннотации на JDK 23+,
# поэтому если по умолчанию в системе стоит JDK новее — берём 21 явно.
#
#   ./build.sh package        то же, что mvn package
#
# Если ваш JAVA_HOME уже указывает на 21, можно звать mvn напрямую.
if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || unset JAVA_HOME
    export JAVA_HOME
fi
exec mvn "$@"
