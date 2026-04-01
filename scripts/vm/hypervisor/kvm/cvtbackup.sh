#!/usr/bin/bash

set -eo pipefail

OP=""
VM=""
BACKUP_DIR=""
DISK_PATHS=""
QUIESCE=""
BACKUP_TYPE="FULL"
CHECKPOINT_NAME=""
PARENT_BACKUP_DIR=""
PARENT_CHECKPOINT_NAME=""
PARENT_CHECKPOINT_PATH=""
BACKUP_FILES=""
DUMMY_VM=""
logFile="/var/log/cloudstack/agent/agent.log"

EXIT_CLEANUP_FAILED=20

log() {
  [[ "$verb" -eq 1 ]] && builtin echo "$@"
  if [[ "$1" == "-ne" || "$1" == "-e" || "$1" == "-n" ]]; then
    builtin echo -e "$(date '+%Y-%m-%d %H-%M-%S>')" "${@: 2}" >> "$logFile"
  else
    builtin echo "$(date '+%Y-%m-%d %H-%M-%S>')" "$@" >> "$logFile"
  fi
}

vercomp() {
  local IFS=.
  local i ver1=($1) ver2=($3)
  for ((i=0; i<${#ver1[@]}; i++)); do
    if [[ -z ${ver2[i]} ]]; then
      ver2[i]=0
    fi
    if ((10#${ver1[i]} > 10#${ver2[i]})); then
      return 0
    elif ((10#${ver1[i]} < 10#${ver2[i]})); then
      return 2
    fi
  done
  return 0
}

sanity_checks() {
  hvVersion=$(virsh version | grep hypervisor | awk '{print $(NF)}')
  libvVersion=$(virsh version | grep libvirt | awk '{print $(NF)}' | tail -n 1)
  apiVersion=$(virsh version | grep API | awk '{print $(NF)}')

  vercomp "$hvVersion" ">=" "4.2.0"
  hvStatus=$?
  vercomp "$libvVersion" ">=" "7.2.0"
  libvStatus=$?

  if [[ $hvStatus -eq 0 && $libvStatus -eq 0 ]]; then
    log -ne "Success... [ QEMU: $hvVersion Libvirt: $libvVersion apiVersion: $apiVersion ]"
  else
    echo "Failure... Your QEMU version $hvVersion or libvirt version $libvVersion is unsupported. Consider upgrading to the required minimum version of QEMU: 4.2.0 and Libvirt: 7.2.0"
    exit 1
  fi
}

cleanup() {
  local status=0
  cleanup_dummy_vm
  rm -rf "$dest" || { echo "Failed to delete $dest"; status=1; }
  if [[ $status -ne 0 ]]; then
    echo "Backup cleanup failed"
    exit $EXIT_CLEANUP_FAILED
  fi
}

cleanup_dummy_vm() {
  if [[ -z "$DUMMY_VM" ]]; then
    return
  fi
  virsh -c qemu:///system destroy "$DUMMY_VM" > /dev/null 2>&1 || true
  virsh -c qemu:///system undefine "$DUMMY_VM" --nvram > /dev/null 2>&1 || true
  DUMMY_VM=""
}

split_csv() {
  local csv="$1"
  IFS=',' read -r -a SPLIT_CSV_RESULT <<< "$csv"
}

get_backup_file_by_index() {
  local index="$1"
  split_csv "$BACKUP_FILES"
  echo "${SPLIT_CSV_RESULT[$index]}"
}

dump_checkpoint_xml() {
  local vm_name="$1"
  if [[ -n "$CHECKPOINT_NAME" ]]; then
    virsh -c qemu:///system checkpoint-dumpxml --domain "$vm_name" --checkpointname "$CHECKPOINT_NAME" --no-domain > "$dest/checkpoints/$CHECKPOINT_NAME.xml" 2>/dev/null || true
  fi
}

redefine_checkpoint_if_needed() {
  local vm_name="$1"
  local checkpoint_file="$2"
  if [[ -z "$PARENT_CHECKPOINT_NAME" || -z "$checkpoint_file" || ! -f "$checkpoint_file" ]]; then
    return
  fi
  if virsh -c qemu:///system checkpoint-info --domain "$vm_name" --checkpointname "$PARENT_CHECKPOINT_NAME" > /dev/null 2>&1; then
    return
  fi
  if ! virsh -c qemu:///system checkpoint-create --domain "$vm_name" --xmlfile "$checkpoint_file" --redefine > /dev/null 2>&1; then
    echo "Failed to redefine checkpoint $PARENT_CHECKPOINT_NAME on domain $vm_name"
    exit 1
  fi
}

create_dummy_vm_xml() {
  local dummy_vm="$1"
  local xml_file="$2"
  {
    echo "<domain type='kvm'>"
    echo "  <name>$dummy_vm</name>"
    echo "  <memory unit='MiB'>256</memory>"
    echo "  <currentMemory unit='MiB'>256</currentMemory>"
    echo "  <vcpu placement='static'>1</vcpu>"
    echo "  <os><type arch='x86_64' machine='pc'>hvm</type></os>"
    echo "  <devices>"
    echo "    <emulator>/usr/bin/qemu-system-x86_64</emulator>"
  } > "$xml_file"

  split_csv "$DISK_PATHS"
  for index in "${!SPLIT_CSV_RESULT[@]}"; do
    local disk_path="${SPLIT_CSV_RESULT[$index]}"
    local disk_name
    disk_name=$(printf "\\$(printf '%03o' $((97 + index)))")
    if [[ "$disk_path" == rbd:* ]]; then
      local source_name="${disk_path#rbd:}"
      source_name="${source_name%%:mon_host=*}"
      local mon_hosts="${disk_path#*:mon_host=}"
      mon_hosts="${mon_hosts%%:*}"
      {
        echo "    <disk type='network' device='disk'>"
        echo "      <driver name='qemu' type='raw'/>"
        echo "      <source protocol='rbd' name='$source_name'>"
        IFS=';' read -r -a hosts <<< "$mon_hosts"
        for host in "${hosts[@]}"; do
          echo "        <host name='$host'/>"
        done
        echo "      </source>"
        echo "      <target dev='vd$disk_name' bus='virtio'/>"
        echo "    </disk>"
      } >> "$xml_file"
    else
      {
        echo "    <disk type='file' device='disk'>"
        echo "      <driver name='qemu' type='qcow2'/>"
        echo "      <source file='$disk_path'/>"
        echo "      <target dev='vd$disk_name' bus='virtio'/>"
        echo "    </disk>"
      } >> "$xml_file"
    fi
  done

  {
    echo "    <console type='pty'/>"
    echo "  </devices>"
    echo "</domain>"
  } >> "$xml_file"
}

create_backup_xml_for_dummy_vm() {
  local backup_xml="$1"
  local checkpoint_xml="$2"

  echo "<domainbackup mode='push'><disks>" > "$backup_xml"
  split_csv "$DISK_PATHS"
  for index in "${!SPLIT_CSV_RESULT[@]}"; do
    local disk_name
    disk_name=$(printf "\\$(printf '%03o' $((97 + index)))")
    local target_file="$dest/$(get_backup_file_by_index "$index")"
    echo "<disk name='vd$disk_name' backup='yes' type='file' backupmode='full'><driver type='qcow2'/><target file='$target_file'/>" >> "$backup_xml"
    if [[ "$BACKUP_TYPE" == "INCREMENTAL" && -n "$PARENT_CHECKPOINT_NAME" ]]; then
      echo "<incremental>$PARENT_CHECKPOINT_NAME</incremental>" >> "$backup_xml"
    fi
    echo "</disk>" >> "$backup_xml"
  done
  echo "</disks></domainbackup>" >> "$backup_xml"

  echo "<domaincheckpoint><name>$CHECKPOINT_NAME</name><disks>" > "$checkpoint_xml"
  for index in "${!SPLIT_CSV_RESULT[@]}"; do
    local disk_name
    disk_name=$(printf "\\$(printf '%03o' $((97 + index)))")
    echo "<disk name='vd$disk_name' checkpoint='bitmap'/>" >> "$checkpoint_xml"
  done
  echo "</disks></domaincheckpoint>" >> "$checkpoint_xml"
}

parse_rbd_uri() {
  local uri="$1"
  RBD_IMAGE="${uri#rbd:}"
  RBD_IMAGE="${RBD_IMAGE%%:mon_host=*}"
  local remainder="${uri#*:mon_host=}"
  RBD_MON_HOSTS="${remainder%%:*}"
}

rbd_cli() {
  local mon_hosts="$1"
  shift
  local mon_arg="${mon_hosts//;/,}"
  rbd -m "$mon_arg" "$@"
}

backup_running_vm() {
  mkdir -p "$dest/checkpoints" || { echo "Failed to create backup directory $dest"; exit 1; }
  local parent_checkpoint_file=""
  if [[ "$BACKUP_TYPE" == "INCREMENTAL" && -n "$PARENT_CHECKPOINT_PATH" ]]; then
    parent_checkpoint_file="$PARENT_CHECKPOINT_PATH"
    redefine_checkpoint_if_needed "$VM" "$parent_checkpoint_file"
  fi

  echo "<domainbackup mode='push'><disks>" > "$dest/backup.xml"
  local index=0
  for disk in $(virsh -c qemu:///system domblklist "$VM" --details 2>/dev/null | awk '/disk/{print $3}'); do
    local target_file="$dest/$(get_backup_file_by_index "$index")"
    echo "<disk name='$disk' backup='yes' type='file' backupmode='full'><driver type='qcow2'/><target file='$target_file'/>" >> "$dest/backup.xml"
    if [[ "$BACKUP_TYPE" == "INCREMENTAL" && -n "$PARENT_CHECKPOINT_NAME" ]]; then
      echo "<incremental>$PARENT_CHECKPOINT_NAME</incremental>" >> "$dest/backup.xml"
    fi
    echo "</disk>" >> "$dest/backup.xml"
    index=$((index + 1))
  done
  echo "</disks></domainbackup>" >> "$dest/backup.xml"

  echo "<domaincheckpoint><name>$CHECKPOINT_NAME</name><disks>" > "$dest/checkpoint.xml"
  for disk in $(virsh -c qemu:///system domblklist "$VM" --details 2>/dev/null | awk '/disk/{print $3}'); do
    echo "<disk name='$disk' checkpoint='bitmap'/>" >> "$dest/checkpoint.xml"
  done
  echo "</disks></domaincheckpoint>" >> "$dest/checkpoint.xml"

  local thaw=0
  if [[ ${QUIESCE} == "true" ]]; then
    if virsh -c qemu:///system qemu-agent-command "$VM" '{"execute":"guest-fsfreeze-freeze"}' > /dev/null 2>/dev/null; then
      thaw=1
    fi
  fi

  local backup_begin=0
  if virsh -c qemu:///system backup-begin --domain "$VM" --backupxml "$dest/backup.xml" --checkpointxml "$dest/checkpoint.xml" > /dev/null 2>&1; then
    backup_begin=1
  fi

  if [[ $thaw -eq 1 ]]; then
    virsh -c qemu:///system qemu-agent-command "$VM" '{"execute":"guest-fsfreeze-thaw"}' > /dev/null 2>&1 || true
  fi

  if [[ $backup_begin -ne 1 ]]; then
    cleanup
    exit 1
  fi

  while true; do
    status=$(virsh -c qemu:///system domjobinfo "$VM" --completed --keep-completed | awk '/Job type:/ {print $3}')
    case "$status" in
      Completed) break ;;
      Failed) echo "Virsh backup job failed"; cleanup ;;
    esac
    sleep 5
  done

  if [[ "$BACKUP_TYPE" == "INCREMENTAL" && -n "$PARENT_BACKUP_DIR" ]]; then
    split_csv "$BACKUP_FILES"
    for backup_file in "${SPLIT_CSV_RESULT[@]}"; do
      qemu-img rebase -u -F qcow2 -b "$PARENT_BACKUP_DIR/$backup_file" "$dest/$backup_file" > /dev/null 2>&1 || true
    done
  fi

  dump_checkpoint_xml "$VM"
  rm -f "$dest/backup.xml" "$dest/checkpoint.xml"
  sync
}

backup_stopped_vm() {
  mkdir -p "$dest/checkpoints" || { echo "Failed to create backup directory $dest"; exit 1; }
  local dummy_vm="DUMMY-VM-${CHECKPOINT_NAME//./-}"
  DUMMY_VM="$dummy_vm"
  local dummy_xml="$dest/dummy-vm.xml"
  local backup_xml="$dest/backup.xml"
  local checkpoint_xml="$dest/checkpoint.xml"

  create_dummy_vm_xml "$dummy_vm" "$dummy_xml"
  virsh -c qemu:///system define "$dummy_xml" > /dev/null
  virsh -c qemu:///system start "$dummy_vm" --paused > /dev/null

  if [[ "$BACKUP_TYPE" == "INCREMENTAL" && -n "$PARENT_CHECKPOINT_PATH" ]]; then
    redefine_checkpoint_if_needed "$dummy_vm" "$PARENT_CHECKPOINT_PATH"
  fi

  create_backup_xml_for_dummy_vm "$backup_xml" "$checkpoint_xml"
  if ! virsh -c qemu:///system backup-begin --domain "$dummy_vm" --backupxml "$backup_xml" --checkpointxml "$checkpoint_xml" > /dev/null 2>&1; then
    echo "Failed to start backup for dummy VM $dummy_vm"
    cleanup
    exit 1
  fi

  while true; do
    status=$(virsh -c qemu:///system domjobinfo "$dummy_vm" --completed --keep-completed | awk '/Job type:/ {print $3}')
    case "$status" in
      Completed) break ;;
      Failed) echo "Virsh backup job failed for dummy VM $dummy_vm"; cleanup ;;
    esac
    sleep 5
  done

  if [[ "$BACKUP_TYPE" == "INCREMENTAL" && -n "$PARENT_BACKUP_DIR" ]]; then
    split_csv "$BACKUP_FILES"
    for backup_file in "${SPLIT_CSV_RESULT[@]}"; do
      qemu-img rebase -u -F qcow2 -b "$PARENT_BACKUP_DIR/$backup_file" "$dest/$backup_file" > /dev/null 2>&1 || true
    done
  fi

  dump_checkpoint_xml "$dummy_vm"
  cleanup_dummy_vm
  rm -f "$backup_xml" "$checkpoint_xml" "$dummy_xml"
  sync
}

backup_rbd_volumes() {
  mkdir -p "$dest/checkpoints" || { echo "Failed to create backup directory $dest"; exit 1; }
  split_csv "$DISK_PATHS"
  for index in "${!SPLIT_CSV_RESULT[@]}"; do
    local disk_path="${SPLIT_CSV_RESULT[$index]}"
    parse_rbd_uri "$disk_path"
    local output_file="$dest/$(get_backup_file_by_index "$index")"
    rbd_cli "$RBD_MON_HOSTS" snap create "${RBD_IMAGE}@${CHECKPOINT_NAME}"
    if [[ "$BACKUP_TYPE" == "INCREMENTAL" && -n "$PARENT_CHECKPOINT_NAME" ]]; then
      rbd_cli "$RBD_MON_HOSTS" export-diff --from-snap "$PARENT_CHECKPOINT_NAME" "${RBD_IMAGE}@${CHECKPOINT_NAME}" "$output_file"
    else
      rbd_cli "$RBD_MON_HOSTS" export "${RBD_IMAGE}@${CHECKPOINT_NAME}" "$output_file"
    fi
  done
}

usage() {
  echo ""
  echo "Usage: $0 -o <operation> -v|--vm <domain name> -p <backup path> -b <FULL|INCREMENTAL> -c <checkpoint name> -r <parent backup path> -i <parent checkpoint name> -j <parent checkpoint path> -f <backup files> -d <disks path> -q|--quiesce <true|false>"
  echo ""
  exit 1
}

while [[ $# -gt 0 ]]; do
  case $1 in
    -o|--operation) OP="$2"; shift; shift ;;
    -v|--vm) VM="$2"; shift; shift ;;
    -p|--path) BACKUP_DIR="$2"; shift; shift ;;
    -b|--backuptype) BACKUP_TYPE="$2"; shift; shift ;;
    -c|--checkpoint) CHECKPOINT_NAME="$2"; shift; shift ;;
    -r|--parentbackup) PARENT_BACKUP_DIR="$2"; shift; shift ;;
    -i|--parentcheckpoint) PARENT_CHECKPOINT_NAME="$2"; shift; shift ;;
    -j|--parentcheckpointpath) PARENT_CHECKPOINT_PATH="$2"; shift; shift ;;
    -f|--backupfiles) BACKUP_FILES="$2"; shift; shift ;;
    -q|--quiesce) QUIESCE="$2"; shift; shift ;;
    -d|--diskpaths) DISK_PATHS="$2"; shift; shift ;;
    -h|--help) usage ;;
    *) echo "Invalid option: $1"; usage ;;
  esac
done

if [[ -z "$BACKUP_DIR" ]]; then
  echo "Backup path (-p|--path) is required"
  exit 1
fi

dest="$BACKUP_DIR"
sanity_checks

if [[ "$OP" != "backup" ]]; then
  echo "Unsupported operation: $OP"
  exit 1
fi

if [[ "$DISK_PATHS" == rbd:* || "$DISK_PATHS" == *",rbd:"* ]]; then
  backup_rbd_volumes
  exit 0
fi

STATE=$(virsh -c qemu:///system list | awk -v vm="$VM" '$2 == vm {print $3}')
if [[ -n "$STATE" && "$STATE" == "running" ]]; then
  backup_running_vm
else
  backup_stopped_vm
fi

exit 0
