set -x

ln -fs ../../meta-awl /home/build/fsdk/meta-awl-fido

meta-awl-plinux/scripts/awl-install-yocto-buildtools.sh

nice ionice -c 2 -n 7 meta-awl-plinux/scripts/awl-do-download-install-sdk-sstate-cache.sh

meta-awl-plinux/scripts/awl-do-setup-supported-machines.sh

cd /home/build/fsdk/v1.9/QorIQ-SDK-V1.9-20151210-yocto/
source /home/build/fsdk/yocto-1.8-buildtools/environment-setup-x86_64-pokysdk-linux
source build_awl-idu7000/SOURCE_THIS

time bitbake awl-image-sit fsl-toolchain

# gather artifacts
cd $WORKSPACE
mkdir artifacts
ln -fs /home/build/fsdk/v1.9/QorIQ-SDK-V1.9-20151210-yocto/build_awl-idu7000/tmp/deploy/images/awl-idu7000 artifacts/
ln -fs /home/build/fsdk/v1.9/QorIQ-SDK-V1.9-20151210-yocto/build_awl-idu7000/tmp/deploy/sdk artifacts/
