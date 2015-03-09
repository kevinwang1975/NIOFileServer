This project is an implementation of file server and the corresponding file client
using Java non-blocking IO. 

With non-blocking IO, the server can handle all client requests in a single
thread, need less system resource, therefore is capable of handling more
client requests compared to the blocking IO implementation, but not without
any disadvantage. As all clients' data are handled in single thread, with a
selector, extra complication in the implementation is the price to pay, 
compared to blocking IO implementation.

On OS X and Linux, "/" is used as path separator, (e.g., /home/user/data/) while 
on Windows, it uses "\" instead, (e.g. C:\users\data). To overcome this issue,
a path when sent between server and client, is broken into a series of strings,
e.g. to retrieve a file "/mydata/file.txt" located in the server root data
directory "/home/user/svrdata", the client will send the path as a string array
{"mydata" "file.txt"} to the server without path separator, the server receives
the string array and converts it into a proper path accordingly.
  
So far it supports the following features:
- List files on the server. when the path is given as an empty string
  array, the file list of the whole root data directory of the server is returned.
- Send file to the server with a path indicating the location where the file
  is to be stored.
- Get file from the server with a path relative to the root data directory.

The file server only needs two parameters:
- port number: on which it listens for the incoming requests
- root data directory: where the data files from the clients will be stored

The file client connects to the server by its IP address and the port. The file
client can be used in a sequential manner, i.e. all tasks are executed one after
another in one single thread; or in a random manner by either employing a thread
pool, or just creating new thread. Examples are given in the main() for each manner.