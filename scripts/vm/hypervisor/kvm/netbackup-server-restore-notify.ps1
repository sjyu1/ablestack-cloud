$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DefaultConfigRoot = 'C:\ProgramData\AbleStack\NetBackup'
$DefaultLogFile = Join-Path $DefaultConfigRoot ("netbackup-mold-restore.{0}.log" -f (Get-Date -Format 'yyyyMMddHHmmssfff'))
$LegacyDefaultLogFile = Join-Path $DefaultConfigRoot 'netbackup-mold-restore.log'
$DefaultConfigFile = Join-Path $DefaultConfigRoot 'restore.conf'

$LogFile = $env:LOG_FILE
if ([string]::IsNullOrWhiteSpace($LogFile)) {
    $LogFile = $DefaultLogFile
} elseif ($LogFile -eq $LegacyDefaultLogFile) {
    $LogFile = $DefaultLogFile
}

$ConfigFile = $env:MOLD_CONFIG_FILE
if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
    $ConfigFile = $DefaultConfigFile
}

$ConfigRoot = $env:CONFIG_ROOT
if ([string]::IsNullOrWhiteSpace($ConfigRoot)) {
    $ConfigRoot = $DefaultConfigRoot
}

$MoldRestoreApiUrl = $env:MOLD_RESTORE_API_URL
$MoldRestoreApiMethod = if ([string]::IsNullOrWhiteSpace($env:MOLD_RESTORE_API_METHOD)) { 'POST' } else { $env:MOLD_RESTORE_API_METHOD }
$MoldRestoreMode = if ([string]::IsNullOrWhiteSpace($env:MOLD_RESTORE_MODE)) { 'live' } else { $env:MOLD_RESTORE_MODE }
$MoldApiResponseFormat = if ([string]::IsNullOrWhiteSpace($env:MOLD_API_RESPONSE_FORMAT)) { 'json' } else { $env:MOLD_API_RESPONSE_FORMAT }
$MoldUrl = $env:MOLD_URL
$AdminApikey = $env:ADMIN_APIKEY
$AdminSecretKey = $env:ADMIN_SECRETKEY
$MoldSecretFile = $env:MOLD_SECRET_FILE
$SecretKeyFile = $env:SECRET_KEY_FILE

function Write-Log {
    param([Parameter(Mandatory = $true)][string]$Message)
    $directory = Split-Path -Parent $LogFile
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    }

    $line = "[{0}] {1}" -f (Get-Date -Format 'MM/dd/yyyy HH:mm:ss.fff'), $Message
    $encoding = [System.Text.UTF8Encoding]::new($false)
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            $stream = [System.IO.File]::Open($LogFile, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
            try {
                $writer = [System.IO.StreamWriter]::new($stream, $encoding)
                try {
                    $writer.WriteLine($line)
                    $writer.Flush()
                    return
                } finally {
                    $writer.Dispose()
                }
            } finally {
                $stream.Dispose()
            }
        } catch {
            if ($attempt -eq 3) {
                throw
            }
            Start-Sleep -Milliseconds (50 * $attempt)
        }
    }
}

function Fail {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Log "RESTORE error: $Message"
    throw $Message
}

function LooksLike-BackupId {
    param([Parameter(Mandatory = $true)][string]$Value)
    return $Value -match '^[^/\\]+_\d+$'
}

function Join-Bytes {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Left,
        [Parameter(Mandatory = $true)][byte[]]$Right
    )

    $joined = New-Object byte[] ($Left.Length + $Right.Length)
    [System.Buffer]::BlockCopy($Left, 0, $joined, 0, $Left.Length)
    [System.Buffer]::BlockCopy($Right, 0, $joined, $Left.Length, $Right.Length)
    return $joined
}

function ByteArrayEquals {
    param(
        [byte[]]$Left,
        [byte[]]$Right
    )

    if ($null -eq $Left -or $null -eq $Right -or $Left.Length -ne $Right.Length) {
        return $false
    }

    for ($i = 0; $i -lt $Left.Length; $i++) {
        if ($Left[$i] -ne $Right[$i]) {
            return $false
        }
    }
    return $true
}

function UrlEncodePlus {
    param([Parameter(Mandatory = $true)][string]$Value)
    return [System.Uri]::EscapeDataString($Value).Replace('%20', '+')
}

function Resolve-SecretFilePath {
    if (-not [string]::IsNullOrWhiteSpace($script:MoldSecretFile)) {
        return $script:MoldSecretFile
    }
    return Join-Path $DefaultConfigRoot 'secrets\secret.enc'
}

function Load-Config {
    if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
        Fail 'MOLD_CONFIG_FILE must be configured'
    }
    if (-not (Test-Path -LiteralPath $ConfigFile -PathType Leaf)) {
        Fail "MOLD_CONFIG_FILE not found: $ConfigFile"
    }

    foreach ($rawLine in Get-Content -LiteralPath $ConfigFile) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#') -or $line -notmatch '=') {
            continue
        }

        $parts = $line.Split('=', 2)
        if ($parts.Count -ne 2) {
            continue
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")

        switch ($key) {
            'MOLD_URL' { if ([string]::IsNullOrWhiteSpace($script:MoldUrl)) { $script:MoldUrl = $value } }
            'ADMIN_APIKEY' { if ([string]::IsNullOrWhiteSpace($script:AdminApikey)) { $script:AdminApikey = $value } }
            'LOG_FILE' { }
            'MOLD_SECRET_FILE' { if ([string]::IsNullOrWhiteSpace($script:MoldSecretFile)) { $script:MoldSecretFile = $value } }
            'SECRET_KEY_FILE' { if ([string]::IsNullOrWhiteSpace($script:SecretKeyFile)) { $script:SecretKeyFile = $value } }
            default { }
        }
    }

    if ([string]::IsNullOrWhiteSpace($script:MoldRestoreApiUrl)) {
        $script:MoldRestoreApiUrl = $script:MoldUrl
    }
    if ([string]::IsNullOrWhiteSpace($script:MoldRestoreApiUrl)) {
        Fail 'MOLD_RESTORE_API_URL or MOLD_URL must be configured'
    }
    if ([string]::IsNullOrWhiteSpace($script:AdminApikey)) {
        Fail 'ADMIN_APIKEY must be configured'
    }
    if ($script:MoldRestoreMode -notin @('live', 'validate-only')) {
        Fail "MOLD_RESTORE_MODE must be either 'live' or 'validate-only'"
    }
}

function Load-Secret {
    if (-not [string]::IsNullOrWhiteSpace($script:AdminSecretKey)) {
        return
    }

    if ([string]::IsNullOrWhiteSpace($script:SecretKeyFile)) {
        Fail 'SECRET_KEY_FILE must be configured'
    }

    $secretFile = Resolve-SecretFilePath
    if (-not (Test-Path -LiteralPath $secretFile -PathType Leaf)) {
        Fail "Secret file not found: $secretFile"
    }
    if (-not (Test-Path -LiteralPath $script:SecretKeyFile -PathType Leaf)) {
        Fail "Secret key file not found: $script:SecretKeyFile"
    }

    $payload = Get-Content -LiteralPath $secretFile -Raw | ConvertFrom-Json
    if ([int]$payload.version -ne 1) {
        Fail "Unsupported encrypted secret format version: $($payload.version)"
    }

    $iterations = [int]$payload.iterations
    $salt = [Convert]::FromBase64String([string]$payload.salt)
    $iv = [Convert]::FromBase64String([string]$payload.iv)
    $ciphertext = [Convert]::FromBase64String([string]$payload.ciphertext)
    $mac = [Convert]::FromBase64String([string]$payload.mac)

    $keyMaterial = [System.Text.Encoding]::UTF8.GetBytes((Get-Content -LiteralPath $script:SecretKeyFile -Raw).Trim())
    $derive = New-Object System.Security.Cryptography.Rfc2898DeriveBytes($keyMaterial, $salt, $iterations, [System.Security.Cryptography.HashAlgorithmName]::SHA256)
    $derived = $derive.GetBytes(48)
    $aesKey = New-Object byte[] 32
    $aesIv = New-Object byte[] 16
    [System.Buffer]::BlockCopy($derived, 0, $aesKey, 0, 32)
    [System.Buffer]::BlockCopy($derived, 32, $aesIv, 0, 16)

    if (-not (ByteArrayEquals -Left $aesIv -Right $iv)) {
        Fail 'Derived IV does not match encrypted secret payload'
    }

    $macKey = [System.Security.Cryptography.SHA256]::Create().ComputeHash((Join-Bytes -Left $aesKey -Right $salt))
    $macInput = Join-Bytes -Left $iv -Right $ciphertext
    $expectedMac = [System.Security.Cryptography.HMACSHA256]::new($macKey).ComputeHash($macInput)
    if (-not (ByteArrayEquals -Left $mac -Right $expectedMac)) {
        Fail 'Encrypted secret MAC validation failed'
    }

    $aes = [System.Security.Cryptography.Aes]::Create()
    $aes.KeySize = 256
    $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
    $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
    $aes.Key = $aesKey
    $aes.IV = $aesIv
    $decryptor = $aes.CreateDecryptor()
    try {
        $plaintext = $decryptor.TransformFinalBlock($ciphertext, 0, $ciphertext.Length)
        $script:AdminSecretKey = [System.Text.Encoding]::UTF8.GetString($plaintext).Trim()
    } finally {
        $decryptor.Dispose()
        $aes.Dispose()
    }

    if ([string]::IsNullOrWhiteSpace($script:AdminSecretKey)) {
        Fail 'Decrypted MOLD secret is empty.'
    }
}

function Build-ApiParams {
    param(
        [Parameter(Mandatory = $true)][string]$CommandName,
        [Parameter(Mandatory = $true)][hashtable]$Params
    )

    $pairs = New-Object System.Collections.Generic.List[string]
    $pairs.Add("command=$(UrlEncodePlus $CommandName)")
    foreach ($entry in $Params.GetEnumerator()) {
        $pairs.Add("$($entry.Key)=$(UrlEncodePlus ([string]$entry.Value))")
    }
    $pairs.Add("response=$(UrlEncodePlus $script:MoldApiResponseFormat)")
    return ($pairs -join '&')
}

function Build-SignedUrl {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [Parameter(Mandatory = $true)][string]$ApiParams
    )

    $sortedParams = New-Object System.Collections.Generic.List[string]
    $sortedParams.Add(("apikey={0}" -f ((UrlEncodePlus $script:AdminApikey).ToLowerInvariant())))

    foreach ($token in $ApiParams -split '&') {
        $parts = $token.Split('=', 2)
        if ($parts.Count -ne 2) {
            continue
        }
        $sortedParams.Add(("{0}={1}" -f $parts[0].ToLowerInvariant(), $parts[1].ToLowerInvariant()))
    }

    $sortedUrl = ($sortedParams | Sort-Object) -join '&'
    $hmac = New-Object System.Security.Cryptography.HMACSHA256 (,[System.Text.Encoding]::UTF8.GetBytes($script:AdminSecretKey))
    $signature = [Convert]::ToBase64String($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($sortedUrl)))
    $encodedApiKey = UrlEncodePlus $script:AdminApikey
    $encodedSignature = UrlEncodePlus $signature
    return "${BaseUrl}?${ApiParams}&apiKey=${encodedApiKey}&signature=${encodedSignature}"
}

function Invoke-MoldApi {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$CommandName,
        [Parameter(Mandatory = $true)][hashtable]$Params,
        [Parameter(Mandatory = $true)][string]$ExternalId,
        [Parameter(Mandatory = $true)][string]$Operation
    )

    Write-Log "RESTORE api-call command=$CommandName method=$Method externalid=$ExternalId operation=$Operation"
    $apiParams = Build-ApiParams -CommandName $CommandName -Params $Params
    $signedUrl = Build-SignedUrl -BaseUrl $script:MoldRestoreApiUrl -ApiParams $apiParams

    try {
        $invokeParams = @{
            Method = $Method
            Uri = $signedUrl
            Headers = @{
                Accept = 'application/json'
                'Content-Type' = 'application/x-www-form-urlencoded'
            }
            TimeoutSec = 300
            ErrorAction = 'Stop'
        }
        if ($PSVersionTable.PSVersion.Major -lt 6) {
            $invokeParams.UseBasicParsing = $true
        }
        $response = Invoke-WebRequest @invokeParams
        return $response.Content
    } catch {
        $message = $_.Exception.Message
        if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
            $message = $_.ErrorDetails.Message
        }
        Fail "Mold API call failed: method=$Method command=$CommandName error=$message"
    }
}

try {
    $ArgsFromCmd = @($args)
    if ($ArgsFromCmd.Count -lt 2) {
        Write-Log "RESTORE missing required netbackup-server-restore-notify arguments args=$($ArgsFromCmd -join ' ')"
        exit 1
    }

    $ProgramName = $ArgsFromCmd[0]
    $ExternalId = $ArgsFromCmd[1]
    $Operation = if ($ArgsFromCmd.Count -ge 3) { $ArgsFromCmd[2] } else { '' }
    $ProcessId = $PID

    Write-Log "START script=netbackup-server-restore-notify.ps1 pid=$ProcessId program=$ProgramName externalid=$ExternalId operation=$Operation args=$($ArgsFromCmd -join ' ')"
    Load-Config
    Load-Secret
    Write-Log "RESTORE config-loaded pid=$ProcessId program=$ProgramName externalid=$ExternalId operation=$Operation mold_url=$MoldRestoreApiUrl log_file=$LogFile"

    if ($MoldRestoreMode -eq 'validate-only') {
        $requestKey = if (LooksLike-BackupId -Value $ExternalId) { 'backupid' } else { 'externalid' }
        Write-Log "RESTORE validate-only pid=$ProcessId command=restoreNetBackup $requestKey=$ExternalId operation=$Operation program=$ProgramName"
        exit 0
    }

    if ($Operation -and $Operation.ToLowerInvariant() -ne 'restore') {
        Write-Log "RESTORE skip pid=$ProcessId externalid=$ExternalId operation=$Operation reason=unsupported-operation"
        exit 0
    }

    $RequestKey = if (LooksLike-BackupId -Value $ExternalId) { 'backupid' } else { 'externalid' }

    $response = Invoke-MoldApi -Method $MoldRestoreApiMethod -CommandName 'restoreNetBackup' -Params @{
        $RequestKey = $ExternalId
    } -ExternalId $ExternalId -Operation $Operation

    Write-Log "RESTORE api-call-success pid=$ProcessId command=restoreNetBackup $RequestKey=$ExternalId operation=$Operation response=$response"
} catch {
    $message = $_.Exception.Message
    if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
        $message = $_.ErrorDetails.Message
    }
    Write-Log "RESTORE error pid=$PID externalid=$ExternalId operation=$Operation message=$message"
    exit 1
}
