#!/bin/bash
set -x

#echo -e "Host git7-advgit\nHostName advgit.advantech.ca\nUser git7\nIdentityFile ~/.ssh/id_rsa.adv\n" >~/.ssh/config
git submodule init && git submodule update

sdk/fsl-qoriq-glibc-x86_64-ppce500v2-toolchain-1.9.sh -y -d /opt/fsl-qoriq/1.9 </dev/null

sudo chown -R jenkins:jenkins /opt/fsl-qoriq
sudo ln -fs /bin/bash /bin/sh
idu-7000/src/scripts/build-idu7000-app-kernel-mod-all.sh V=1 all
