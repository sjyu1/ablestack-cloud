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
$MoldAsyncJobPollIntervalSeconds = if ([string]::IsNullOrWhiteSpace($env:MOLD_ASYNC_JOB_POLL_INTERVAL)) { 5 } else { [int]$env:MOLD_ASYNC_JOB_POLL_INTERVAL }
$MoldAsyncJobTimeoutSeconds = if ([string]::IsNullOrWhiteSpace($env:MOLD_ASYNC_JOB_TIMEOUT)) { 1800 } else { [int]$env:MOLD_ASYNC_JOB_TIMEOUT }
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

function Get-UnixEpochSeconds {
    return [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
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
    Write-Log "RESTORE signature-input baseurl=$BaseUrl sortedUrl=$sortedUrl"
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
    Write-Log "RESTORE request-url command=$CommandName method=$Method externalid=$ExternalId operation=$Operation url=$signedUrl"

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

function Find-JsonPropertyValue {
    param(
        [Parameter(Mandatory = $true)][object]$InputObject,
        [Parameter(Mandatory = $true)][string]$PropertyName
    )

    if ($null -eq $InputObject) {
        return $null
    }

    if ($InputObject -is [string]) {
        return $null
    }

    if ($InputObject -is [System.Collections.IDictionary]) {
        foreach ($entry in $InputObject.GetEnumerator()) {
            if ($entry.Key -and $entry.Key.ToString().Equals($PropertyName, [System.StringComparison]::OrdinalIgnoreCase)) {
                return $entry.Value
            }
            $nested = Find-JsonPropertyValue -InputObject $entry.Value -PropertyName $PropertyName
            if ($null -ne $nested) {
                return $nested
            }
        }
        return $null
    }

    if ($InputObject -is [System.Collections.IEnumerable]) {
        foreach ($item in $InputObject) {
            $nested = Find-JsonPropertyValue -InputObject $item -PropertyName $PropertyName
            if ($null -ne $nested) {
                return $nested
            }
        }
        return $null
    }

    foreach ($property in $InputObject.PSObject.Properties) {
        if ($property.Name.Equals($PropertyName, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $property.Value
        }
        $nested = Find-JsonPropertyValue -InputObject $property.Value -PropertyName $PropertyName
        if ($null -ne $nested) {
            return $nested
        }
    }

    return $null
}

function Format-LogValue {
    param([Parameter(Mandatory = $true)][object]$Value)

    if ($null -eq $Value) {
        return ''
    }
    if ($Value -is [string]) {
        return $Value
    }
    return ($Value | ConvertTo-Json -Compress -Depth 20)
}

function Get-AsyncJobResponse {
    param([Parameter(Mandatory = $true)][string]$ResponseJson)

    $parsed = $ResponseJson | ConvertFrom-Json
    $asyncResponse = Find-JsonPropertyValue -InputObject $parsed -PropertyName 'queryasyncjobresultresponse'
    if ($null -eq $asyncResponse) {
        return $parsed
    }
    return $asyncResponse
}

function Get-AsyncJobFailureText {
    param([Parameter(Mandatory = $true)][object]$AsyncResponse)

    $jobResultCode = Find-JsonPropertyValue -InputObject $AsyncResponse -PropertyName 'jobresultcode'
    $errorText = Find-JsonPropertyValue -InputObject $AsyncResponse -PropertyName 'errortext'
    $jobResult = Find-JsonPropertyValue -InputObject $AsyncResponse -PropertyName 'jobresult'

    $parts = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace([string]$jobResultCode)) {
        $parts.Add("jobresultcode=$jobResultCode")
    }

    if ($jobResult -is [string]) {
        if ([string]::IsNullOrWhiteSpace([string]$errorText)) {
            $errorText = $jobResult
        }
        if (-not [string]::IsNullOrWhiteSpace($jobResult)) {
            $parts.Add("jobresult=$jobResult")
        }
    } elseif ($null -ne $jobResult) {
        $nestedErrorCode = Find-JsonPropertyValue -InputObject $jobResult -PropertyName 'errorcode'
        $nestedErrorText = Find-JsonPropertyValue -InputObject $jobResult -PropertyName 'errortext'

        if (-not [string]::IsNullOrWhiteSpace([string]$nestedErrorCode)) {
            $parts.Add("jobresult.errorcode=$nestedErrorCode")
        }
        if (-not [string]::IsNullOrWhiteSpace([string]$nestedErrorText)) {
            $parts.Add("jobresult.errortext=$nestedErrorText")
        }

        if ([string]::IsNullOrWhiteSpace([string]$errorText)) {
            if (-not [string]::IsNullOrWhiteSpace([string]$nestedErrorText)) {
                $errorText = $nestedErrorText
            } elseif (-not [string]::IsNullOrWhiteSpace([string]$nestedErrorCode)) {
                $errorText = "errorcode=$nestedErrorCode"
            } else {
                $errorText = Format-LogValue $jobResult
            }
        }

        $parts.Add("jobresult=$(Format-LogValue $jobResult)")
    }

    if (-not [string]::IsNullOrWhiteSpace([string]$errorText)) {
        $parts.Insert(0, "errortext=$errorText")
    }

    if ($parts.Count -eq 0) {
        return 'unknown error'
    }

    return ($parts -join '; ')
}

function Wait-MoldAsyncJob {
    param(
        [Parameter(Mandatory = $true)][string]$JobId,
        [Parameter(Mandatory = $true)][string]$OperationName,
        [Parameter(Mandatory = $true)][string]$RequestKey,
        [Parameter(Mandatory = $true)][string]$RequestValue,
        [Parameter(Mandatory = $true)][string]$ExternalId,
        [string]$Operation = ''
    )

    $deadline = (Get-Date).AddSeconds($MoldAsyncJobTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $response = Invoke-MoldApi -Method $MoldRestoreApiMethod -CommandName 'queryAsyncJobResult' -Params @{
            jobid = $JobId
        } -ExternalId $ExternalId -Operation $Operation

        $asyncResponse = Get-AsyncJobResponse -ResponseJson $response
        $jobStatusValue = Find-JsonPropertyValue -InputObject $asyncResponse -PropertyName 'jobstatus'
        $jobStatus = if ($null -eq $jobStatusValue -or [string]::IsNullOrWhiteSpace([string]$jobStatusValue)) { 0 } else { [int]$jobStatusValue }
        Write-Log "RESTORE async-job-poll pid=$PID command=$OperationName jobid=$JobId status=$jobStatus $RequestKey=$RequestValue"

        switch ($jobStatus) {
            0 {
                Start-Sleep -Seconds $MoldAsyncJobPollIntervalSeconds
                continue
            }
            1 {
                $jobResult = Find-JsonPropertyValue -InputObject $asyncResponse -PropertyName 'jobresult'
                Write-Log "RESTORE async-job-success pid=$PID command=$OperationName jobid=$JobId status=1 result=$(Format-LogValue $jobResult)"
                return $asyncResponse
            }
            2 {
                $failureText = Get-AsyncJobFailureText -AsyncResponse $asyncResponse
                Write-Log "RESTORE async-job-failed pid=$PID command=$OperationName jobid=$JobId status=2 details=$failureText"
                throw "Mold async job failed for $OperationName jobId=${JobId}: $failureText"
            }
            default {
                Write-Log "RESTORE async-job-unexpected pid=$PID command=$OperationName jobid=$JobId status=$jobStatus"
                Start-Sleep -Seconds $MoldAsyncJobPollIntervalSeconds
            }
        }
    }

    Write-Log "RESTORE async-job-timeout pid=$PID command=$OperationName jobid=$JobId timeoutSeconds=$MoldAsyncJobTimeoutSeconds"
    throw "Timed out waiting for Mold async job $OperationName jobId=$JobId after $MoldAsyncJobTimeoutSeconds seconds"
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

    $response = Invoke-MoldApi -Method $MoldRestoreApiMethod -CommandName 'prepareNetBackupRestore' -Params @{
        $RequestKey = $ExternalId
    } -ExternalId $ExternalId -Operation $Operation

    $precheckResponse = Get-AsyncJobResponse -ResponseJson $response
    $shouldRestoreValue = Find-JsonPropertyValue -InputObject $precheckResponse -PropertyName 'shouldrestore'
    $skipReason = Find-JsonPropertyValue -InputObject $precheckResponse -PropertyName 'skipreason'
    $resolvedVmId = Find-JsonPropertyValue -InputObject $precheckResponse -PropertyName 'vmid'
    $resolvedVmName = Find-JsonPropertyValue -InputObject $precheckResponse -PropertyName 'vmname'
    $resolvedBackupUuid = Find-JsonPropertyValue -InputObject $precheckResponse -PropertyName 'backupuuid'
    Write-Log "RESTORE precheck-response pid=$ProcessId command=prepareNetBackupRestore $RequestKey=$ExternalId operation=$Operation shouldrestore=$shouldRestoreValue vmid=$resolvedVmId vmname=$resolvedVmName backupuuid=$resolvedBackupUuid skipreason=$skipReason response=$response"

    if ([string]::IsNullOrWhiteSpace([string]$shouldRestoreValue) -or -not [System.Convert]::ToBoolean($shouldRestoreValue)) {
        $reasonText = if ([string]::IsNullOrWhiteSpace([string]$skipReason)) { 'precheck-declined' } else { [string]$skipReason }
        Write-Log "RESTORE precheck-skip pid=$ProcessId command=prepareNetBackupRestore $RequestKey=$ExternalId operation=$Operation vmid=$resolvedVmId vmname=$resolvedVmName backupuuid=$resolvedBackupUuid reason=$reasonText"
        exit 0
    }

    $response = Invoke-MoldApi -Method $MoldRestoreApiMethod -CommandName 'restoreNetBackup' -Params @{
        $RequestKey = $ExternalId
    } -ExternalId $ExternalId -Operation $Operation

    $jobIdValue = Find-JsonPropertyValue -InputObject ($response | ConvertFrom-Json) -PropertyName 'jobid'
    if ([string]::IsNullOrWhiteSpace([string]$jobIdValue)) {
        Write-Log "RESTORE api-call-response pid=$ProcessId command=restoreNetBackup $RequestKey=$ExternalId operation=$Operation response=$response"
        throw "Mold restoreNetBackup API did not return jobid for externalid=$ExternalId"
    }

    $jobId = [string]$jobIdValue
    Write-Log "RESTORE async-job-submitted pid=$ProcessId command=restoreNetBackup jobid=$jobId $RequestKey=$ExternalId operation=$Operation response=$response"
    $finalResponse = Wait-MoldAsyncJob -JobId $jobId -OperationName 'restoreNetBackup' -RequestKey $RequestKey -RequestValue $ExternalId -ExternalId $ExternalId -Operation $Operation
    Write-Log "RESTORE async-job-complete pid=$ProcessId command=restoreNetBackup jobid=$jobId $RequestKey=$ExternalId operation=$Operation response=$(Format-LogValue $finalResponse)"
} catch {
    $message = $_.Exception.Message
    if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
        $message = $_.ErrorDetails.Message
    }
    Write-Log "RESTORE error pid=$PID externalid=$ExternalId operation=$Operation message=$message"
    exit 1
}
