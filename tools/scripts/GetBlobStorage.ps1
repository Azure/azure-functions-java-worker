<#
.Description
GetBlobStorage.ps1 outputs the storage accounts in the given subscription that contain the given blob container name.

.PARAMETER SubscriptionId
The Azure subscription ID.

.EXAMPLE
PS> .\GetBlobStorage.ps1 -SubscriptionId "60edc399-b9af-4991-95ec-0e16573f1622" -BlobContainerName "MyBlobContainer"

#>
[CmdletBinding(DefaultParametersetname="BySubscriptionId")]
param (
    [Parameter(Mandatory=$true, ParameterSetName="BySubscriptionId")]
    [ValidateNotNullOrEmpty()]
    [String]
    $SubscriptionId,

    [Parameter(Mandatory=$true, ParameterSetName="BySubscriptionId")]
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

WriteLog -Message "Script started."
ValidatePrerequisites

WriteLog "Setting current session context to SubscriptionId '$SubscriptionId'."
Set-AzContext -Subscription $SubscriptionId | Out-Null

WriteLog "Analyzing storage accounts for subscription '$SubscriptionId'..."

$count = 0

# Get all the storage accounts in this subscription
foreach ($storageAccount in @(Get-AzStorageAccount))
{
    Write-Verbose "Get key for storage account '$($storageAccount.StorageAccountName)'."
    $keys = Get-AzStorageAccountKey -Name $storageAccount.StorageAccountName -ResourceGroupName $storageAccount.ResourceGroupName

    Write-Verbose "Connecting to storage account..."
    $context = New-AzStorageContext -StorageAccountName $storageAccount.StorageAccountName -StorageAccountKey $keys[0].Value

    # Check if the blob container exists                
    $blob = Get-AzStorageContainer -Name $BlobContainerName -Context $context -ErrorAction SilentlyContinue

    if (-not $blob)
    {
        $message = "Blob container '$BlobContainerName' not found in storage account '$($storageAccount.StorageAccountName). Skipping...'"
        Write-Verbose $message

        # Move to the next storage account
        continue
    }

    # Get the blob access policy
    $acl = Get-AzStorageContainerAcl -Name $BlobContainerName -Context $context -ErrorAction Continue

    if ($acl -and $acl.PublicAccess -ne "Off")
    {
        if ($count -eq 0)
        {
            WriteLog "Found storage accounts containing the public '$BlobContainerName' blob container."
        }

        $count++
        Write-Output $storageAccount.StorageAccountName
    }
}

if ($count -gt 0)
{
    WriteLog "There are $count storage accounts in this subscription that contain the public '$BlobContainerName' blob container."
}
else
{
    WriteLog "There are no storage accounts in this subscription that contain the public '$BlobContainerName' blob container."
}
