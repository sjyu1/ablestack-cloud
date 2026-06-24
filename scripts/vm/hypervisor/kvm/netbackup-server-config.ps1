param(
    [Parameter(Mandatory = $true)]
    [string]$MoldUrl,

    [Parameter(Mandatory = $true)]
    [string]$AdminApiKey,

    [Parameter(Mandatory = $true)]
    [string]$AdminSecretKey,

    [string]$RestoreScriptOutputDir = "",
    [string]$RestoreConfigOutputDir = "C:\ProgramData\AbleStack\NetBackup",
    [string]$RestoreSecretOutputDir = "C:\ProgramData\AbleStack\NetBackup\secrets",
    [string]$NetBackupServerSecretKeyFile = "C:\ProgramData\AbleStack\NetBackup\ablestack.key",
    [string]$LogFile = "C:\ProgramData\AbleStack\NetBackup\netbackup-mold-restore.log",
    [string]$NetBackupStagingRoot = "/tmp/mold/netbackup"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$WindowsNetBackupBinCommonCandidates = @(
    'C:\Program Files\Cohesity NetBackup\NetBackup\bin',
    'C:\Program Files (x86)\Cohesity NetBackup\NetBackup\bin',
    'C:\Program Files\Veritas\NetBackup\bin',
    'C:\Program Files (x86)\Veritas\NetBackup\bin'
)
$WindowsNetBackupExecutableCandidates = @('bpclntcmd.exe', 'bplist.exe', 'bprd.exe')
$WindowsNetBackupRegistryKeys = @(
    'SOFTWARE\Cohesity NetBackup\NetBackup',
    'SOFTWARE\WOW6432Node\Cohesity NetBackup\NetBackup',
    'SOFTWARE\Veritas\NetBackup',
    'SOFTWARE\WOW6432Node\Veritas\NetBackup'
)
$WindowsNetBackupConfigDefault = 'C:\ProgramData\AbleStack\NetBackup'

function Write-Log {
    param([Parameter(Mandatory = $true)][string]$Message)
    $directory = Split-Path -Parent $LogFile
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    }
    Add-Content -LiteralPath $LogFile -Value ("[{0}] {1}" -f (Get-Date -Format 'MM/dd/yyyy HH:mm:ss.fff'), $Message) -Encoding UTF8
}

function Fail {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Log "FAIL $Message"
    throw $Message
}

function Backup-ExistingFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return
    }
    $stamp = Get-Date -Format 'yyyyMMddHHmmss'
    $backup = Join-Path (Split-Path -Parent $Path) ("{0}.bak.{1}" -f (Split-Path -Leaf $Path), $stamp)
    Move-Item -LiteralPath $Path -Destination $backup -Force
    Write-Host "Backed up existing file: $Path -> $backup"
}

function Join-Bytes {
    param(
        [byte[]]$Left,
        [byte[]]$Right
    )
    $joined = New-Object byte[] ($Left.Length + $Right.Length)
    [System.Buffer]::BlockCopy($Left, 0, $joined, 0, $Left.Length)
    [System.Buffer]::BlockCopy($Right, 0, $joined, $Left.Length, $Right.Length)
    return $joined
}

function Write-Utf8NoBomText {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Get-WindowsNetBackupBinDir {
    param([string]$Override)
    if (-not [string]::IsNullOrWhiteSpace($Override)) {
        return (Resolve-Path -LiteralPath $Override).Path
    }

    foreach ($registryKey in $WindowsNetBackupRegistryKeys) {
        try {
            $path = (Get-ItemProperty -Path ("Registry::HKEY_LOCAL_MACHINE\{0}" -f $registryKey) -Name InstallPath -ErrorAction Stop).InstallPath
            if (-not [string]::IsNullOrWhiteSpace($path)) {
                $candidate = Join-Path $path 'bin'
                if (Test-Path -LiteralPath $candidate -PathType Container) {
                    return (Resolve-Path -LiteralPath $candidate).Path
                }
            }
        } catch {
            continue
        }
    }

    foreach ($candidate in $WindowsNetBackupBinCommonCandidates) {
        if (Test-Path -LiteralPath $candidate -PathType Container) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "Unable to resolve Windows NetBackup bin directory. Set RestoreScriptOutputDir explicitly."
}

function Protect-Secret {
    param(
        [Parameter(Mandatory = $true)][string]$SecretKeyFile,
        [Parameter(Mandatory = $true)][string]$Plaintext
    )

    $iterations = 200000
    $salt = New-Object byte[] 16
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($salt)
    $secretKeyBytes = [System.Text.Encoding]::UTF8.GetBytes((Get-Content -LiteralPath $SecretKeyFile -Raw).Trim())
    $derive = New-Object System.Security.Cryptography.Rfc2898DeriveBytes(
        $secretKeyBytes,
        $salt,
        $iterations,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256
    )
    $derived = $derive.GetBytes(48)
    $aesKey = New-Object byte[] 32
    $aesIv = New-Object byte[] 16
    [System.Buffer]::BlockCopy($derived, 0, $aesKey, 0, 32)
    [System.Buffer]::BlockCopy($derived, 32, $aesIv, 0, 16)

    $aes = [System.Security.Cryptography.Aes]::Create()
    $aes.KeySize = 256
    $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
    $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
    $aes.Key = $aesKey
    $aes.IV = $aesIv
    try {
        $encryptor = $aes.CreateEncryptor()
        try {
            $plaintextBytes = [System.Text.Encoding]::UTF8.GetBytes($Plaintext)
            $ciphertext = $encryptor.TransformFinalBlock($plaintextBytes, 0, $plaintextBytes.Length)
        } finally {
            $encryptor.Dispose()
        }
    } finally {
        $aes.Dispose()
    }

    $macKey = [System.Security.Cryptography.SHA256]::Create().ComputeHash((Join-Bytes -Left $aesKey -Right $salt))
    $macInput = Join-Bytes -Left $aesIv -Right $ciphertext
    $mac = [System.Security.Cryptography.HMACSHA256]::new($macKey).ComputeHash($macInput)

    $payload = [ordered]@{
        version = 1
        iterations = $iterations
        salt = [Convert]::ToBase64String($salt)
        iv = [Convert]::ToBase64String($aesIv)
        ciphertext = [Convert]::ToBase64String($ciphertext)
        mac = [Convert]::ToBase64String($mac)
    }
    return ($payload | ConvertTo-Json -Compress)
}

function Write-SecretKeyFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    Backup-ExistingFile -Path $Path
    Set-Content -LiteralPath $Path -Value 'QWJsZWNsb3VkMSE=' -Encoding Ascii
    [System.IO.File]::SetAttributes($Path, [System.IO.FileAttributes]::Normal)
}

function Write-EncryptedSecretFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Secret,
        [Parameter(Mandatory = $true)][string]$SecretKeyFile
    )
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    Backup-ExistingFile -Path $Path
    $json = Protect-Secret -SecretKeyFile $SecretKeyFile -Plaintext $Secret
    Write-Utf8NoBomText -Path $Path -Content $json
}

function Write-RestoreConfigFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$SecretPath,
        [Parameter(Mandatory = $true)][string]$SecretKeyFile
    )
    Backup-ExistingFile -Path $Path
    $content = @(
        "MOLD_URL=`"$MoldUrl`"",
        "ADMIN_APIKEY=`"$AdminApiKey`"",
        "MOLD_SECRET_FILE=`"$SecretPath`"",
        "SECRET_KEY_FILE=`"$SecretKeyFile`"",
        "LOG_FILE=`"$LogFile`"",
        "NETBACKUP_STAGING_ROOT=`"$NetBackupStagingRoot`""
    ) -join [Environment]::NewLine
    Write-Utf8NoBomText -Path $Path -Content $content
}

function Copy-RestoreNotifyFiles {
    param([Parameter(Mandatory = $true)][string]$DestinationDir)
    New-Item -ItemType Directory -Force -Path $DestinationDir | Out-Null
    $sources = @(
        (Join-Path $ScriptDir 'netbackup-server-restore-notify.cmd'),
        (Join-Path $ScriptDir 'netbackup-server-restore-notify.ps1')
    )
    $targets = @{
        'netbackup-server-restore-notify.cmd' = 'restore_notify.cmd'
        'netbackup-server-restore-notify.ps1' = 'restore_notify.ps1'
    }
    foreach ($source in $sources) {
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Required restore notify source file not found: $source"
        }
        $leaf = Split-Path -Leaf $source
        $targetName = $targets[$leaf]
        if ([string]::IsNullOrWhiteSpace($targetName)) {
            throw "No target mapping defined for restore notify source file: $leaf"
        }
        $target = Join-Path $DestinationDir $targetName
        Backup-ExistingFile -Path $target
        Copy-Item -LiteralPath $source -Destination $target -Force
    }
}

if ([string]::IsNullOrWhiteSpace($MoldUrl)) { Fail 'MoldUrl is required.' }
if ([string]::IsNullOrWhiteSpace($AdminApiKey)) { Fail 'AdminApiKey is required.' }
if ([string]::IsNullOrWhiteSpace($AdminSecretKey)) { Fail 'AdminSecretKey is required.' }

$restoreScriptOutputDirResolved = Get-WindowsNetBackupBinDir -Override $RestoreScriptOutputDir
$restoreConfigOutputDirResolved = if ([string]::IsNullOrWhiteSpace($RestoreConfigOutputDir)) { $WindowsNetBackupConfigDefault } else { $RestoreConfigOutputDir }
$restoreSecretOutputDirResolved = if ([string]::IsNullOrWhiteSpace($RestoreSecretOutputDir)) { Join-Path $restoreConfigOutputDirResolved 'secrets' } else { $RestoreSecretOutputDir }
$secretKeyPathResolved = if ([string]::IsNullOrWhiteSpace($NetBackupServerSecretKeyFile)) { Join-Path $restoreConfigOutputDirResolved 'ablestack.key' } else { $NetBackupServerSecretKeyFile }
$restoreConfigPath = Join-Path $restoreConfigOutputDirResolved 'restore.conf'
$secretPath = Join-Path $restoreSecretOutputDirResolved 'secret.enc'

New-Item -ItemType Directory -Force -Path $restoreConfigOutputDirResolved | Out-Null
New-Item -ItemType Directory -Force -Path $restoreSecretOutputDirResolved | Out-Null

Write-SecretKeyFile -Path $secretKeyPathResolved
Write-RestoreConfigFile -Path $restoreConfigPath -SecretPath $secretPath -SecretKeyFile $secretKeyPathResolved
Write-EncryptedSecretFile -Path $secretPath -Secret $AdminSecretKey -SecretKeyFile $secretKeyPathResolved
Copy-RestoreNotifyFiles -DestinationDir $restoreScriptOutputDirResolved

Write-Host ""
Write-Host "Generated NetBackup server files:"
Write-Host "  Restore cfg: $restoreConfigPath"
Write-Host "  Secret key : $secretKeyPathResolved"
Write-Host "  Secret(enc): $secretPath"
Write-Host "  Scripts dir: $restoreScriptOutputDirResolved"
