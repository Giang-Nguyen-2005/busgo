using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Collections;
using System.Runtime.InteropServices;

namespace BusGoLocal {
    public class Result { public int Code; public string Output; }
    public static class Native {
        [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern IntPtr CreateJobObject(IntPtr attributes, string name);
        [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern IntPtr OpenJobObject(uint access, bool inherit, string name);
        [DllImport("kernel32.dll", SetLastError=true)] static extern bool SetInformationJobObject(IntPtr job, int kind, IntPtr data, uint size);
        [DllImport("kernel32.dll", SetLastError=true)] static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
        [DllImport("kernel32.dll", SetLastError=true)] static extern bool IsProcessInJob(IntPtr process, IntPtr job, out bool result);
        [DllImport("kernel32.dll", SetLastError=true)] static extern bool TerminateJobObject(IntPtr job, uint code);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        [StructLayout(LayoutKind.Sequential)] struct Basic { public long Time1,Time2; public uint Flags; public UIntPtr Min,Max; public uint Active; public UIntPtr Affinity; public uint Priority,Scheduling; }
        [StructLayout(LayoutKind.Sequential)] struct IO { public ulong A,B,C,D,E,F; }
        [StructLayout(LayoutKind.Sequential)] struct Extended { public Basic Basic; public IO IO; public UIntPtr ProcessMemory,JobMemory,PeakProcess,PeakJob; }
        static IntPtr ownedJob;
        public static void OwnJob(string name) {
            ownedJob=CreateJobObject(IntPtr.Zero,name);
            if(ownedJob==IntPtr.Zero || Marshal.GetLastWin32Error()==183) throw new Exception("Cannot create unique launcher job.");
            var limits=new Extended(); limits.Basic.Flags=0x2000; // KILL_ON_JOB_CLOSE
            int size=Marshal.SizeOf(limits); IntPtr memory=Marshal.AllocHGlobal(size);
            try { Marshal.StructureToPtr(limits,memory,false);
                if(!SetInformationJobObject(ownedJob,9,memory,(uint)size) || !AssignProcessToJobObject(ownedJob,Process.GetCurrentProcess().Handle)) throw new Exception("Cannot own worker process tree.");
            } finally { Marshal.FreeHGlobal(memory); }
        }
        public static bool InJob(string name,int pid) {
            IntPtr job=OpenJobObject(4,false,name); if(job==IntPtr.Zero) return false;
            try { using(var p=Process.GetProcessById(pid)) { bool member; return IsProcessInJob(p.Handle,job,out member)&&member; } }
            catch { return false; } finally { CloseHandle(job); }
        }
        public static void StopJob(string name,int ownerPid) {
            IntPtr job=OpenJobObject(4|8,false,name); if(job==IntPtr.Zero) throw new Exception("Launcher job is unavailable; nothing was killed.");
            try { using(var p=Process.GetProcessById(ownerPid)) { bool member;
                if(!IsProcessInJob(p.Handle,job,out member)||!member) throw new Exception("Worker job identity mismatch; nothing was killed.");
                if(!TerminateJobObject(job,0)) throw new Exception("Cannot stop verified launcher job.");
            } } finally { CloseHandle(job); }
        }
        public static string Quote(string text) {
            var b=new StringBuilder("\""); int slashes=0;
            foreach(char c in text) {
                if(c=='\\') {slashes++;continue;}
                if(c=='\"') {b.Append('\\',slashes*2+1);b.Append(c);}
                else {b.Append('\\',slashes);b.Append(c);} slashes=0;
            }
            b.Append('\\',slashes*2);b.Append('"');return b.ToString();
        }
        public static string Redact(string text,string[] secrets) {
            foreach(string secret in secrets) if(!String.IsNullOrEmpty(secret)) text=text.Replace(secret,"[REDACTED]");
            return Regex.Replace(text,@"(?i)(Bearer\s+|(?:password|secret|token)\s*[=:]\s*)\S+","$1[REDACTED]");
        }
        public static ProcessStartInfo Info(string file,string[] args,string directory,IDictionary environment) {
            var info=new ProcessStartInfo(); info.UseShellExecute=false; info.CreateNoWindow=true;
            info.WorkingDirectory=directory; var quoted=new StringBuilder();
            foreach(string arg in args) { if(quoted.Length>0) quoted.Append(' '); quoted.Append(Quote(arg)); }
            if(file.EndsWith(".cmd",StringComparison.OrdinalIgnoreCase)||file.EndsWith(".bat",StringComparison.OrdinalIgnoreCase)) {
                // Fixed launcher commands only. No user input or secrets are interpolated here.
                info.FileName=Environment.GetEnvironmentVariable("ComSpec"); info.Arguments="/d /s /c \""+Quote(file)+" "+quoted+"\"";
            } else { info.FileName=file; info.Arguments=quoted.ToString(); }
            if(environment!=null) {
                var remove=new System.Collections.Generic.List<string>();
                foreach(string key in info.EnvironmentVariables.Keys)
                    if(Regex.IsMatch(key,@"^(DB_|MYSQL_|JWT_|SPRING_|BUSGO_|MAIL_|SERVER_|COMPOSE_|VITE_|API_PROXY_TARGET$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$|NODE_OPTIONS$)",RegexOptions.IgnoreCase)) remove.Add(key);
                foreach(string key in remove) info.EnvironmentVariables.Remove(key);
                foreach(DictionaryEntry item in environment) info.EnvironmentVariables[(string)item.Key]=(string)item.Value;
            }
            return info;
        }
        public static Result Run(string file,string[] args,string directory,IDictionary environment,string log,string[] secrets,int timeoutSeconds) {
            var info=Info(file,args,directory,environment); info.RedirectStandardOutput=true; info.RedirectStandardError=true;
            info.StandardOutputEncoding=new UTF8Encoding(false);info.StandardErrorEncoding=new UTF8Encoding(false);
            var output=new StringBuilder(); object gate=new object();
            using(var writer=String.IsNullOrEmpty(log)?null:new StreamWriter(log,true,new UTF8Encoding(false)))
            using(var process=new Process()) {
                process.StartInfo=info;
                DataReceivedEventHandler receive=(sender,e)=>{ if(e.Data==null)return; lock(gate) {
                    if(writer!=null) {writer.WriteLine(Redact(e.Data,secrets));writer.Flush();}
                    else output.AppendLine(e.Data); // Captured internally, never automatically printed.
                }};
                process.OutputDataReceived+=receive;process.ErrorDataReceived+=receive;
                process.Start();process.BeginOutputReadLine();process.BeginErrorReadLine();
                if(!process.WaitForExit(timeoutSeconds*1000)) { try{process.Kill();}catch{} throw new Exception("Command timed out; inspect launcher logs."); }
                process.WaitForExit();return new Result{Code=process.ExitCode,Output=output.ToString()};
            }
        }
        public static Process Spawn(string file,string[] args,string directory) {
            return Process.Start(Info(file,args,directory,null));
        }
        public static Process SpawnWithEnvironment(string file,string[] args,string directory,IDictionary environment) {
            return Process.Start(Info(file,args,directory,environment));
        }
    }
}
