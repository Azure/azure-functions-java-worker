<#
.Description
UpdateBlobStorage.ps1 updates the given blob container name.

.PARAMETER SubscriptionId
The Azure subscription ID.

.PARAMETER FunctionAppName
The name of the function app.

.PARAMETER StorageAccountName
The name of the storage accountName.

.EXAMPLE
PS> .\UpdateBlobStorage.ps1 -SubscriptionId "60edc399-b9af-4991-95ec-0e16573f1622" -FunctionAppName "myFunctionApp1235" -BlobContainerName "MyBlobContainer"

.EXAMPLE
PS> .\UpdateBlobStorage.ps1 -SubscriptionId "60edc399-b9af-4991-95ec-0e16573f1622" -StorageAccountName "storage9a925dc5" -BlobContainerName "MyBlobContainer"

#>
[CmdletBinding(DefaultParametersetname="ByFunctionAppName")]
param (
    [Parameter(Mandatory=$true, ParameterSetName="ByFunctionAppName")]
    [Parameter(Mandatory=$true, ParameterSetName="ByStorageAccountName")]
    [ValidateNotNullOrEmpty()]
    [String]
    $SubscriptionId,

    [Parameter(Mandatory=$true, ParameterSetName="ByFunctionAppName")]
    [ValidateNotNullOrEmpty()]
    [String]
    $FunctionAppName,

    [Parameter(Mandatory=$true, ParameterSetName="ByStorageAccountName")]
    [ValidateNotNullOrEmpty()]
    [String]
    $StorageAccountName,

    [Parameter(Mandatory=$true, ParameterSetName="ByFunctionAppName")]
    [Parameter(Mandatory=$true, ParameterSetName="ByStorageAccountName")]
    [ValidateNotNullOrEmpty()]
    [String]
    $BlobContainerName
)

$ErrorActionPreference = 'Stop'

function WriteLog
{
    param (
        [Parameter(Mandatory=$true)]
        [ValidateNotNullOrEmpty()]
        [string]
        $Message,

        [Switch]
        $Throw
    )

    $Message = (Get-Date -Format G)  + " -- $Message"

    if ($Throw)
    {
        throw $Message
    }

    Write-Host $Message
}

function ValidatePrerequisites
{
    WriteLog "Validating Prerequisites..."

$command = @'
iex "& { $(irm 'https://aka.ms/install-powershell.ps1') } -UseMSI"
'@
    # Make sure we are running on PowerShell Core 7 or higher
    if ($PSVersionTable.PSVersion.Major -lt 7)
    {
        $message = "This script only runs on PowerShell 7 or higher. To install the latest PowerShell, run the following command from an elevated PowerShell window: $command"
        WriteLog -Message $message -Throw
    }

    # Make sure the Az module version 5.5 or higher is installed
    $AzModule = Get-Module -ListAvailable Az -ErrorAction SilentlyContinue
    if ((-not $AzModule) -or ($AzModule.Version.Major -ne 5 -and $AzModule.Version.Minor -lt 5))
    {
        $helpUrl = "https://docs.microsoft.com/en-us/powershell/azure/uninstall-az-ps?view=azps-5.5.0"
        $message = "This script requires Az version 5.5 or higher. Please remove any existing intallation(s). After that run 'Install-Module Az'."
        $message += " For more information on uninstalling Az or AzureRM, please see '$helpUrl'"
        
        WriteLog -Message $message -Throw
    }
}

function GetStorageAccountKey
{
    param
    (
        [Parameter(Mandatory=$true)]
        [ValidateNotNullOrEmpty()]
        [string]
        $StorageAccountName
    )

    $storageAccount = Get-AzStorageAccount | Where-Object { $_.StorageAccountName -eq $StorageAccountName }

    if (-not $storageAccount)
    {
        $message = "Failed to find storage account '$StorageAccountName' in this subscription."
        WriteLog $message -Throw
    }

    $keys = Get-AzStorageAccountKey -Name $storageAccount.StorageAccountName -ResourceGroupName $storageAccount.ResourceGroupName
    
    return $keys[0].Value
}

function GetFunctionApp
{
    param
    (
        [Parameter(Mandatory=$true)]
        [ValidateNotNullOrEmpty()]
        [string]
        $Name
    )

    # Find the function app in this subscription
    $functionApp = Get-AzFunctionApp | Where-Object { $_.Name -eq $Name }

    if (-not $functionApp)
    {
        WriteLog "Function app name '$Name' does not exist in this subscription." -Throw
    }

    return $functionApp
}

# Script constants
$WEBSITERUNFROMPACKAGENAME = "WEBSITE_RUN_FROM_PACKAGE"

function UpdateJavaFunctionAppBlobStorage
{
    [CmdletBinding(DefaultParametersetname="ByFunctionAppName")]
    param (
        [Parameter(Mandatory=$true, ParameterSetName="ByFunctionAppName")]
        [ValidateNotNullOrEmpty()]
        [String]
        $FunctionAppName,

        [Parameter(Mandatory=$true, ParameterSetName="ByStorageAccountName")]
        [ValidateNotNullOrEmpty()]
        [String]
        $StorageAccountName,

        [Parameter(Mandatory=$true, ParameterSetName="ByFunctionAppName")]
        [Parameter(Mandatory=$true, ParameterSetName="ByStorageAccountName")]
        [ValidateNotNullOrEmpty()]
        [String]
        $BlobContainerName
    )
    
    if ($PsCmdlet.ParameterSetName -eq "ByFunctionAppName")
    {
        WriteLog "Getting function app name '$FunctionAppName'."
        $app = GetFunctionApp -Name $FunctionAppName

        if (-not $app.ApplicationSettings.ContainsKey($WEBSITERUNFROMPACKAGENAME))
        {
            WriteLog "Function app name '$FunctionAppName' does not have a '$WEBSITERUNFROMPACKAGENAME' app setting." -Throw
        }

        WriteLog "Inspecting $WEBSITERUNFROMPACKAGENAME app setting."
        $value = $app.ApplicationSettings[$WEBSITERUNFROMPACKAGENAME]

        if ([string]::IsNullOrEmpty($value))
        {
            WriteLog "$WEBSITERUNFROMPACKAGENAME is null or empty." -Throw
        }
        elseif ($value -eq 1)
        {
            WriteLog "$WEBSITERUNFROMPACKAGENAME is set to 1: no further action required."
            return
        }
        elseif ($value -like "*Microsoft.KeyVault*")
        {
            $message = "$WEBSITERUNFROMPACKAGENAME contains the following Key Vault reference: $value"
            $message += [System.Environment]::NewLine
            $message += "Please retrieve the storage account name from the Key Vault and restart this script with the following parameters:"
            $message += [System.Environment]::NewLine
            $message += ".\UpdateBlobStorage.ps1 -SubscriptionId <SubscriptionId> -StorageAccountName <StorageAccountName> -BlobContainerName <BlobContainerName>"
            WriteLog -Message $message -Throw
        }

        WriteLog -Message "Parse storage account name from the '$WEBSITERUNFROMPACKAGENAME' app setting."
        $urlObject = New-Object System.Uri $value

        $storageAccountName = $urlObject.Host.ToString().Split(".")[0]
    }

    WriteLog "Get key for storage account '$storageAccountName'."
    $storageAccountKey = GetStorageAccountKey -StorageAccountName $storageAccountName

    WriteLog "Connecting to storage account..."
    $context = New-AzStorageContext -StorageAccountName $storageAccountName -StorageAccountKey $storageAccountKey

    WriteLog -Message "Set blob container ACL."
    Set-AzStorageContainerAcl -Name $BlobContainerName `
                              -Permission 'Off' `
                              -Context $context

    if ($PsCmdlet.ParameterSetName -eq "ByFunctionAppName")
    {
        $message = "Blob container '$BlobContainerName' for function app '$($app.Name)' successfully updated."
    }
    elseif ($PsCmdlet.ParameterSetName -eq "ByStorageAccountName")
    {
        $message = "Blob container '$BlobContainerName' on storage account '$StorageAccountName' successfully updated."
    }

    WriteLog -Message $message
}

WriteLog -Message "Script started."
ValidatePrerequisites

WriteLog "Setting current session context to SubscriptionId '$SubscriptionId'."
Set-AzContext -Subscription $SubscriptionId | Out-Null

if ($PsCmdlet.ParameterSetName -eq "ByFunctionAppName")
{
    UpdateJavaFunctionAppBlobStorage -FunctionAppName $FunctionAppName -BlobContainerName $BlobContainerName
}
elseif ($PsCmdlet.ParameterSetName -eq "ByStorageAccountName")
{
    UpdateJavaFunctionAppBlobStorage -StorageAccountName $StorageAccountName -BlobContainerName $BlobContainerName
}

WriteLog -Message "Script completed."
