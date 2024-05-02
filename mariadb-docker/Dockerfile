# syntax=docker/dockerfile:1

# The image is for testing MariaDB isolation violations with Jepsen
#
# Usage: docker run ubuntu-22.04/jepsen:v1 mariadb_branch_name
#
# Build me with the following command:
# PROGRESS_NO_TRUNC=1 DOCKER_BUILDKIT=1 BUILDKIT_PROGRESS=plain BUILDKIT_STEP_LOG_MAX_SIZE=1024000000 BUILDKIT_STEP_LOG_MAX_SPEED=1024000000 docker build --progress plain -t ubuntu-22.04/jepsen:v1 .
#
FROM ubuntu:22.04
RUN <<EOT
cp /etc/apt/sources.list /etc/apt/sources.list~
sed -Ei 's/^# deb-src /deb-src /' /etc/apt/sources.list
apt update
apt -y upgrade
apt -y install dpkg-dev
apt -y install git cmake gcc g++ vim libncurses-dev gnutls-dev bison make automake libtool pkg-config libaio-dev libpcre2-dev zlib1g-dev default-jre default-jdk wget
# The following is neccessaery to run lein
apt -y install sudo git openjdk-21-jre openjdk-21-jdk openssh-server openssh-client wget
# The following is neccessary fo Jepsen, I am not sure the all packages are neccessary, it's just copied from jepsen/src/jepsen/os/debian.clj
apt -y install apt-transport-https libzip4 curl man-db faketime netcat-openbsd ntpdate unzip iptables psmisc tar bzip2 iputils-ping iproute2 rsyslog logrotate dirmngr tcpdump
apt -y install graphviz gnuplot
EOT

RUN adduser --disabled-password -gecos "" mariadb

USER mariadb

RUN <<EOT
# SSH is not neccessary for single-machine tests, but Jepsen itself can
# require it for some cases, so just leave it here just in case.
#ssh-keygen -b 2048 -t rsa -f ~/.ssh/localhost -q -N ""

cd ~
git clone https://github.com/vlad-lesin/jepsen-mysql
wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
EOT

USER root
RUN chmod a+x /home/mariadb/lein

USER mariadb
RUN <<EOT
cd ~/jepsen-mysql
# Download all neccessary for the project
../lein run test --help
EOT

USER root
# SSH is not neccessary for single-machine tests, but Jepsen itself can
# require it for some cases, so just leave it here just in case.
#RUN <<EOT
#bash -c 'echo -e "root\nroot\n" | passwd root'
#sed -i 's,^#\?PermitRootLogin .*,PermitRootLogin yes,g' /etc/ssh/sshd_config
#mkdir -p ~/.ssh
#chmod 700 ~/.ssh
#cp /home/mariadb/.ssh/localhost.pub ~/.ssh/authorized_keys
#chmod 644 ~/.ssh/authorized_keys
#echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers
#adduser mariadb sudo
#EOT
COPY ./start.sh /

USER mariadb
RUN <<EOT
# SSH is not neccessary for single-machine tests, but Jepsen itself can
# require it for some cases, so just leave it here just in case.

#sudo service ssh start
#ssh-keyscan -t rsa localhost >> ~/.ssh/known_hosts
#mkdir -p ~/mariadb-bin/data
mkdir -p ~/mariadb-bin/tmp
EOT

ENTRYPOINT ["/start.sh"]
