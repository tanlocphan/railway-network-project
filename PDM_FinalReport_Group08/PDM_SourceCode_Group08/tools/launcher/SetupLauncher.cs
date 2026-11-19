// =====================================================================
//  Setup.exe - starts Railway Setup (SetupApp.jar) without a console window.
//
//  1. Finds a Java 17+ on this computer: JAVA_HOME, the registry, the usual
//     install folders (Oracle, Temurin, Microsoft, Zulu, Corretto, IntelliJ
//     .jdks) and PATH. A JDK (with javac) is preferred over a JRE.
//  2. No Java? Offers to install Eclipse Temurin 21 JDK with winget.
//  3. Starts javaw -jar SetupApp.jar with JAVA_HOME and PATH pointing to
//     that Java, so the build and git commands work even if Java was just
//     installed and Windows has not refreshed PATH yet.
//
//  Built by scripts/build-setup.ps1 with the C# compiler that ships with
//  Windows (C# 5 syntax only).
// =====================================================================
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: System.Reflection.AssemblyTitle("Railway Setup")]
[assembly: System.Reflection.AssemblyProduct("Railway Network and Timetable")]
[assembly: System.Reflection.AssemblyVersion("1.0.0.0")]

static class SetupLauncher
{
    const int MinimumMajor = 17;
    const string Title = "Railway Setup";
    const string WingetId = "EclipseAdoptium.Temurin.21.JDK";

    class JavaHome
    {
        public string Path;
        public int Major;
        public bool IsJdk;
    }

    [STAThread]
    static int Main()
    {
        Application.EnableVisualStyles();
        string folder = AppDomain.CurrentDomain.BaseDirectory;
        string jar = System.IO.Path.Combine(folder, "SetupApp.jar");
        if (!File.Exists(jar))
        {
            MessageBox.Show("SetupApp.jar is missing next to Setup.exe.\n\nRebuild it with:\n"
                + "powershell -ExecutionPolicy Bypass -File scripts\\build-setup.ps1",
                Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }

        JavaHome java = FindJava();
        if (java == null)
        {
            java = OfferInstall();
            if (java == null)
            {
                return 1;
            }
        }

        try
        {
            string javaw = System.IO.Path.Combine(java.Path, "bin\\javaw.exe");
            ProcessStartInfo info = new ProcessStartInfo(javaw,
                "-Dfile.encoding=UTF-8 -jar \"" + jar + "\"");
            info.UseShellExecute = false;
            info.WorkingDirectory = folder;
            info.EnvironmentVariables["JAVA_HOME"] = java.Path;
            info.EnvironmentVariables["PATH"] = System.IO.Path.Combine(java.Path, "bin") + ";"
                + Environment.GetEnvironmentVariable("PATH");
            Process.Start(info);
            return 0;
        }
        catch (Exception e)
        {
            MessageBox.Show("Java could not be started:\n" + e.Message, Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    // ----- finding Java ----------------------------------------------------

    static JavaHome FindJava()
    {
        List<string> candidates = new List<string>();
        AddCandidate(candidates, Environment.GetEnvironmentVariable("JAVA_HOME"));

        foreach (RegistryView view in new RegistryView[] { RegistryView.Registry64, RegistryView.Registry32 })
        {
            ReadRegistry(candidates, view, @"SOFTWARE\JavaSoft\JDK", "JavaHome");
            ReadRegistry(candidates, view, @"SOFTWARE\JavaSoft\Java Development Kit", "JavaHome");
            ReadRegistry(candidates, view, @"SOFTWARE\JavaSoft\JRE", "JavaHome");
            ReadRegistryNested(candidates, view, @"SOFTWARE\Eclipse Adoptium\JDK", @"hotspot\MSI", "Path");
            ReadRegistryNested(candidates, view, @"SOFTWARE\Eclipse Foundation\JDK", @"hotspot\MSI", "Path");
            ReadRegistryNested(candidates, view, @"SOFTWARE\Microsoft\JDK", @"hotspot\MSI", "Path");
            ReadRegistryNested(candidates, view, @"SOFTWARE\Azul Systems\Zulu", "", "InstallationPath");
        }

        string[] roots = new string[] {
            Environment.GetEnvironmentVariable("ProgramW6432"),
            Environment.GetEnvironmentVariable("ProgramFiles"),
            Environment.GetEnvironmentVariable("LOCALAPPDATA") == null ? null
                : System.IO.Path.Combine(Environment.GetEnvironmentVariable("LOCALAPPDATA"), "Programs")
        };
        string[] vendors = new string[] { "Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft", "Zulu",
            "Amazon Corretto", "BellSoft", "Semeru", "OpenJDK" };
        foreach (string root in roots)
        {
            if (string.IsNullOrEmpty(root)) continue;
            foreach (string vendor in vendors)
            {
                AddChildren(candidates, System.IO.Path.Combine(root, vendor));
            }
        }
        AddChildren(candidates, System.IO.Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".jdks"));

        string path = Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (string entry in path.Split(';'))
        {
            string dir = entry.Trim().Trim('"');
            if (dir.Length == 0 || dir.ToLowerInvariant().Contains("javapath")) continue;
            if (File.Exists(System.IO.Path.Combine(dir, "javaw.exe")))
            {
                AddCandidate(candidates, System.IO.Path.GetDirectoryName(dir.TrimEnd('\\')));
            }
        }

        JavaHome best = null;
        foreach (string candidate in candidates)
        {
            JavaHome home = Inspect(candidate);
            if (home == null || home.Major < MinimumMajor) continue;
            if (best == null
                || (home.IsJdk && !best.IsJdk)
                || (home.IsJdk == best.IsJdk && home.Major > best.Major))
            {
                best = home;
            }
        }
        return best;
    }

    static void AddCandidate(List<string> list, string folder)
    {
        if (string.IsNullOrEmpty(folder)) return;
        folder = folder.Trim().Trim('"').TrimEnd('\\');
        foreach (string existing in list)
        {
            if (string.Equals(existing, folder, StringComparison.OrdinalIgnoreCase)) return;
        }
        list.Add(folder);
    }

    static void AddChildren(List<string> list, string folder)
    {
        try
        {
            if (!Directory.Exists(folder)) return;
            foreach (string child in Directory.GetDirectories(folder))
            {
                AddCandidate(list, child);
            }
        }
        catch (Exception)
        {
            // folder not readable: skip it
        }
    }

    static void ReadRegistry(List<string> list, RegistryView view, string keyPath, string valueName)
    {
        try
        {
            using (RegistryKey baseKey = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
            using (RegistryKey key = baseKey.OpenSubKey(keyPath))
            {
                if (key == null) return;
                foreach (string version in key.GetSubKeyNames())
                {
                    using (RegistryKey sub = key.OpenSubKey(version))
                    {
                        if (sub != null) AddCandidate(list, sub.GetValue(valueName) as string);
                    }
                }
            }
        }
        catch (Exception)
        {
            // no access: skip
        }
    }

    static void ReadRegistryNested(List<string> list, RegistryView view, string keyPath, string nested, string valueName)
    {
        try
        {
            using (RegistryKey baseKey = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
            using (RegistryKey key = baseKey.OpenSubKey(keyPath))
            {
                if (key == null) return;
                foreach (string version in key.GetSubKeyNames())
                {
                    string path = nested.Length == 0 ? version : version + "\\" + nested;
                    using (RegistryKey sub = key.OpenSubKey(path))
                    {
                        if (sub != null) AddCandidate(list, sub.GetValue(valueName) as string);
                    }
                }
            }
        }
        catch (Exception)
        {
            // no access: skip
        }
    }

    /** Reads the "release" file of a Java folder: JAVA_VERSION="21.0.5" or "1.8.0_421". */
    static JavaHome Inspect(string folder)
    {
        try
        {
            if (!File.Exists(System.IO.Path.Combine(folder, "bin\\javaw.exe"))) return null;
            int major = 0;
            string release = System.IO.Path.Combine(folder, "release");
            if (File.Exists(release))
            {
                Match m = Regex.Match(File.ReadAllText(release), "JAVA_VERSION=\"(\\d+)(?:\\.(\\d+))?");
                if (m.Success)
                {
                    major = int.Parse(m.Groups[1].Value);
                    if (major == 1 && m.Groups[2].Success) major = int.Parse(m.Groups[2].Value);
                }
            }
            if (major == 0)
            {
                Match m = Regex.Match(System.IO.Path.GetFileName(folder), "(?:jdk|jre|java)[-_]?(\\d+)", RegexOptions.IgnoreCase);
                if (m.Success) major = int.Parse(m.Groups[1].Value);
            }
            JavaHome home = new JavaHome();
            home.Path = folder;
            home.Major = major;
            home.IsJdk = File.Exists(System.IO.Path.Combine(folder, "bin\\javac.exe"));
            return home;
        }
        catch (Exception)
        {
            return null;
        }
    }

    // ----- installing Java -------------------------------------------------

    static JavaHome OfferInstall()
    {
        DialogResult answer = MessageBox.Show(
            "Railway Setup needs Java " + MinimumMajor + " or newer (a JDK), and none was found on this computer.\n\n"
            + "Install Eclipse Temurin 21 JDK now? (about 190 MB, free)\n\n"
            + "Yes: install it automatically with winget\n"
            + "No: open the download page instead",
            Title, MessageBoxButtons.YesNoCancel, MessageBoxIcon.Question);
        if (answer == DialogResult.No)
        {
            OpenDownloadPage();
            return null;
        }
        if (answer != DialogResult.Yes) return null;

        try
        {
            ProcessStartInfo info = new ProcessStartInfo("winget",
                "install --id " + WingetId + " -e --accept-package-agreements --accept-source-agreements");
            info.UseShellExecute = true;
            using (Process p = Process.Start(info))
            {
                p.WaitForExit();
            }
        }
        catch (Exception)
        {
            MessageBox.Show("winget is not available on this computer. The download page will open instead.",
                Title, MessageBoxButtons.OK, MessageBoxIcon.Information);
            OpenDownloadPage();
            return null;
        }

        JavaHome java = FindJava();
        if (java == null)
        {
            MessageBox.Show("Java was not found after the installation.\n\nIf the installation finished, open Setup.exe again.",
                Title, MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
        return java;
    }

    static void OpenDownloadPage()
    {
        try
        {
            Process.Start("https://adoptium.net/temurin/releases/?version=21&os=windows");
        }
        catch (Exception)
        {
            // nothing more to do
        }
    }
}
