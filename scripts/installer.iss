; RavenClient installer (Inno Setup).
;
; Produces a single RavenClient_<version>.exe that lays down the whole jpackage
; app-image (RavenClient.exe + app/ + bundled JRE) into a per-user folder so the
; built-in self-updater can rewrite app.jar / libs without admin rights.
; Installed layout matches what AppUpdater.isPackaged() expects, so the client's
; update.json check + batch swap keeps working like any other update.
;
; Build:  ISCC.exe /DMyAppVersion=1.0.62 scripts\installer.iss

#ifndef MyAppVersion
#define MyAppVersion "0.0.0"
#endif

#define MyAppName "RavenClient"
#define MyAppExeName "RavenClient.exe"
#define SourceDir "..\target\RavenClient"

[Setup]
AppId={{B7F3C1A2-8E4D-4A9B-9C1E-5D2F6A0B3C4D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=RavenClient
AppPublisherURL=https://github.com/wispalol/ravenclient-releases
DefaultDirName={localappdata}\Programs\RavenClient
DefaultGroupName=RavenClient
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=..\target
OutputBaseFilename=RavenClient_{#MyAppVersion}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
SetupIconFile=..\src\main\resources\raven.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes
VersionInfoVersion={#MyAppVersion}.0
VersionInfoProductName=RavenClient
VersionInfoProductVersion={#MyAppVersion}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\RavenClient"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\RavenClient"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch RavenClient"; Flags: nowait postinstall skipifsilent
