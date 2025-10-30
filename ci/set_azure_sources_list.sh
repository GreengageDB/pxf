#!/bin/bash
set -eux
[ $(id -u) -ne 0 ] && exit 1 || true

sed -i.bak 's#http://archive\.ubuntu\.com/ubuntu#http://azure\.archive\.ubuntu\.com/ubuntu#g' /etc/apt/sources.list
sed -i     's#http://[a-z][a-z]\.archive\.ubuntu\.com/ubuntu#http://azure\.archive\.ubuntu\.com/ubuntu#g' /etc/apt/sources.list
sed -i     's#http://security\.ubuntu\.com/ubuntu#http://azure\.archive\.ubuntu\.com/ubuntu#g' /etc/apt/sources.list
sed -i     's#http://[a-z][a-z]\.security\.ubuntu\.com/ubuntu#http://azure\.archive\.ubuntu\.com/ubuntu#g' /etc/apt/sources.list
