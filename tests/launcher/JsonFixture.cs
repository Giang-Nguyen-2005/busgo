// One-response loopback HTTP fixture. No application, Docker or database involved.
using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
public sealed class BusGoJsonFixture : IDisposable {
    readonly TcpListener listener; readonly Thread thread;
    public readonly int Port;
    public BusGoJsonFixture(string json) {
        listener=new TcpListener(IPAddress.Loopback,0);listener.Start();
        Port=((IPEndPoint)listener.LocalEndpoint).Port;
        thread=new Thread(()=>{
            try { using(var client=listener.AcceptTcpClient()) using(var stream=client.GetStream()) {
                stream.ReadTimeout=3000;
                byte[] buffer=new byte[4096];var request=new StringBuilder();
                while(request.Length<16384 && request.ToString().IndexOf("\r\n\r\n",StringComparison.Ordinal)<0) {
                    int count=stream.Read(buffer,0,buffer.Length);if(count==0)return;
                    request.Append(Encoding.ASCII.GetString(buffer,0,count));
                }
                byte[] body=Encoding.UTF8.GetBytes(json);
                byte[] headers=Encoding.ASCII.GetBytes("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+body.Length+"\r\nConnection: close\r\n\r\n");
                stream.Write(headers,0,headers.Length);stream.Write(body,0,body.Length);
            }} catch(SocketException) {} catch(System.IO.IOException) {}
            finally {listener.Stop();}
        });thread.IsBackground=true;thread.Start();
    }
    public void Dispose(){listener.Stop();thread.Join(4000);}
}
