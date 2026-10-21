# Ethical Hacking Lab --- Commands and GUI Practical Guide

> **Purpose:** A Git-friendly quick reference for the tools covered in
> the Ethical Hacking lab discussions: Sysmon, theHarvester,
> NetDiscover, Google Dorking, FOCA, WHOIS, `nslookup`, Sublist3r,
> Maltego, Shodan, Nikto, Nmap, Nessus, Wireshark, Robtex, and Armitage.
>
> **Safety:** Run active scans only against systems you own or have
> explicit permission to test. Use `example.com`, localhost, or an
> isolated lab VM for demonstrations. Public lookup tools can return
> stale or incomplete data. Do not treat a tool result as proof of
> vulnerability without validation.

------------------------------------------------------------------------

## 0. Environment and target checks

### Windows PowerShell

``` powershell
# Show network adapters and IPv4 configuration
ipconfig /all

# Show routes
route print

# Check reachability (does not prove a service is open)
ping 127.0.0.1

# Resolve a domain
nslookup example.com

# Check whether the local Nessus web interface responds
Test-NetConnection -ComputerName localhost -Port 8834

# Find downloaded Nessus installers
Get-ChildItem "$env:USERPROFILE\Downloads" -Filter "*Nessus*.exe" |
    Select-Object Name, Length, LastWriteTime
```

### Kali Linux terminal

``` bash
# Show addresses and routes
ip -br addr
ip route

# Check tool availability
command -v nmap
command -v nikto
command -v whois
command -v sublist3r
command -v netdiscover
command -v theHarvester
command -v armitage
command -v msfconsole

# Check versions where supported
nmap --version
nikto -Version
msfconsole --version

# DNS lookup
nslookup example.com
```

**Network note:** The Kali VM has previously used VirtualBox NAT with an
address such as `10.0.2.15`. In NAT mode, tools may see the virtual NAT
network rather than your physical Wi-Fi LAN. Check `ip -br addr` and
`ip route` before selecting a lab subnet. Never guess the target range.

------------------------------------------------------------------------

## 1. Sysmon (Windows GUI)

**Purpose:** Sysmon records selected system activity in Windows Event
Log. It is a telemetry tool, not an antivirus scanner.

### Open the UI

1.  Press `Win + R`.
2.  Run `eventvwr.msc`.
3.  Open **Applications and Services Logs → Microsoft → Windows → Sysmon
    → Operational**.
4.  Filter or inspect events by time and Event ID.

### Useful event IDs

    Event ID Typical meaning
  ---------- --------------------------------------------------
           1 Process creation
           3 Network connection (if enabled in configuration)
           5 Process termination
           7 Image/DLL loaded (if enabled)
          10 Process access
          11 File created
      12--14 Registry events
          22 DNS query
          16 Sysmon configuration changed

Event IDs and collected fields depend on the Sysmon version and
configuration.

### Check service (PowerShell)

``` powershell
Get-Service Sysmon*
```

Open Event Viewer as an administrator if permissions require it. Do not
replace an existing Sysmon configuration unless you understand the
effect on logging.

------------------------------------------------------------------------

## 2. theHarvester (Kali CLI)

**Purpose:** Passive reconnaissance using public sources to find
domain-related emails, hostnames, and subdomains. Source support varies
by version.

``` bash
theHarvester -h
```

Check the installed version's help first. An example invocation may look
like:

``` bash
theHarvester -d example.com -b duckduckgo
```

If the selected source is unsupported, rate-limited, or requires an API
key, use a source listed by `theHarvester -h`. No results do not prove
that no public information exists.

------------------------------------------------------------------------

## 3. NetDiscover (Kali CLI)

**Purpose:** ARP-based discovery of devices on a local IPv4 network
segment.

``` bash
netdiscover -h
ip -br addr
ip route
```

Only after verifying the authorized lab subnet, run a discovery command
in this form:

``` bash
sudo netdiscover -i eth0 -r <AUTHORIZED_LAB_SUBNET/CIDR>
```

Example **only if** your isolated lab subnet is confirmed as
`10.0.2.0/24`:

``` bash
sudo netdiscover -i eth0 -r 10.0.2.0/24
```

Interpretation: - IP address: responding device's address on the local
segment. - MAC address: layer-2 hardware address observed by ARP. -
Vendor: may be inferred from the MAC prefix and can be missing or
misleading.

NetDiscover is not a general Internet scanner. In VirtualBox NAT mode,
its results may not include devices on the physical Wi-Fi network.

------------------------------------------------------------------------

## 4. Google Dorking (browser)

**Purpose:** Use advanced search operators to locate publicly indexed
information. Use only for legitimate, authorized research.

Examples to understand operators:

``` text
site:example.com
site:example.com filetype:pdf
"exact phrase" site:example.com
site:example.com intitle:documentation
site:example.com -jobs
```

Operators: - `site:` limits results to a site or domain. - `filetype:`
filters by file type. - Quotation marks search for a phrase. -
`intitle:` searches page titles. - `inurl:` searches URL text. - `-word`
excludes a term.

Do not use search operators to seek private credentials, exposed
personal data, or unauthorized access.

------------------------------------------------------------------------

## 5. FOCA (Windows GUI)

**Purpose:** Extract metadata from supported public documents. Metadata
can include author/creator fields, software names, timestamps, and
sometimes internal path details.

### Open and use the UI

1.  Launch `FOCA.exe` from the folder where it was extracted.
2.  Create a project and enter a domain you are authorized to assess.
3.  Use FOCA's document search options and supported search engines.
4.  Download only documents you are allowed to analyze.
5.  Run the metadata analysis and inspect the resulting fields.
6.  Record the document/source and metadata fields in your lab notes.

### Notes

-   Search engines may rate-limit FOCA, block requests, or return no
    results.
-   A zero-result search does not prove that a domain has no documents.
-   Metadata can be missing, stale, or inaccurate.
-   Do not download or redistribute documents containing sensitive
    information.

No universal CLI command is provided because FOCA is primarily used
through its Windows GUI.

------------------------------------------------------------------------

## 6. WHOIS / RDAP

**Purpose:** Inspect public domain registration and network allocation
data. Privacy services and registry policies can hide registrant
details.

### Kali Linux

Check availability:

``` bash
command -v whois
```

Install if needed:

``` bash
sudo apt update
sudo apt install whois
```

Query a public example domain:

``` bash
whois example.com
```

Useful fields may include registrar, domain status, dates, and
nameservers. Output depends on the registry and may be redacted.

### Browser alternative

Open the ICANN Lookup service:

https://lookup.icann.org/

Enter the domain and inspect the available registration data. For IP
allocation, use an appropriate regional registry/RDAP service.

------------------------------------------------------------------------

## 7. nslookup (Windows or Kali CLI)

**Purpose:** Query DNS records.

``` bash
# Default address lookup
nslookup example.com

# Record types
nslookup -type=A example.com
nslookup -type=AAAA example.com
nslookup -type=MX example.com
nslookup -type=NS example.com
nslookup -type=TXT example.com
nslookup -type=CNAME www.example.com
```

Interpretation: - `A`: IPv4 address. - `AAAA`: IPv6 address. - `MX`:
mail exchanger records. - `NS`: nameservers. - `TXT`: text records,
often including verification or email-security data. - `CNAME`: alias to
another DNS name.

Some record types may return no answer for a particular name. That is
not necessarily an error. On Windows, `nslookup -version` may enter
interactive mode rather than display a version; use `Ctrl+C` to exit.

------------------------------------------------------------------------

## 8. Sublist3r (Kali CLI)

**Purpose:** Passive subdomain enumeration using supported public
sources. Results depend on source availability and version.

``` bash
sublist3r -h
```

Example on a domain you are authorized to research:

``` bash
sublist3r -d example.com
```

Select supported engines and save results when the installed version
offers those options:

``` bash
sublist3r -d example.com -e google,bing,yahoo -o subs.txt
cat subs.txt
```

If an engine fails due to API changes, blocking, or rate limits, inspect
`sublist3r -h` and use supported sources. Do not interpret a partial
list as exhaustive.

------------------------------------------------------------------------

## 9. Maltego (GUI)

**Purpose:** Visual OSINT relationship mapping. Maltego uses entities
and transforms to discover related information from available data
sources.

### Workflow

1.  Open Maltego from the Windows Start menu.
2.  Sign in if required and select the available free/community option,
    if offered.
3.  Create a **New Graph**.
4.  Drag a **Domain** entity onto the graph.
5.  Set the value to `example.com` for a demonstration.
6.  Right-click the entity and inspect **Run Transform** or the
    equivalent menu.
7.  Select an available basic DNS/domain-to-IP transform.
8.  Review the returned nodes and connecting lines.
9.  Save the graph if your edition supports it.

### Interpret results

-   **Entity/node:** a domain, IP address, nameserver, or other object.
-   **Transform:** an operation that queries a source for related
    information.
-   **Edge/line:** a relationship reported by the source.
-   No results may indicate a source limitation, missing credentials, or
    unavailable data; it does not prove no relationship exists.

A graph connection does not by itself prove common ownership. Validate
important findings independently.

------------------------------------------------------------------------

## 10. Shodan (browser GUI)

**Purpose:** Search Shodan's index of Internet-connected services and
their observed banners. Shodan results are indexed observations, not a
guarantee of a service's current state.

### Workflow

1.  Open https://www.shodan.io/
2.  Sign in if needed.
3.  Search a broad product query such as:

``` text
product:nginx
```

4.  Open a result and inspect the reported IP, port, service/banner,
    organization or ASN, and observation date if available.
5.  Use the results for passive research; do not attempt to access or
    exploit a host without authorization.

### Interpretation cautions

-   A banner may be old or incomplete.
-   A public IP may belong to a cloud provider or CDN.
-   A detected service is not automatically vulnerable.
-   Search filters and result access may depend on account privileges.

Shodan is primarily web-based for this practical; no CLI commands are
required.

------------------------------------------------------------------------

## 11. Nikto (Kali CLI)

**Purpose:** Web server scanner that checks for known risky files,
server configuration issues, outdated components, and other potential
findings.

### Check installation

``` bash
command -v nikto
nikto -Version
```

Install if needed:

``` bash
sudo apt update
sudo apt install nikto
```

### Run only against a local/authorized web server

First verify that the lab web server is yours and is running. For a
local test service:

``` bash
curl -I http://127.0.0.1/
```

If a local web server is intentionally running on that address, scan it:

``` bash
nikto -h http://127.0.0.1/
```

If the service uses a different lab address, replace the target only
with the confirmed, authorized host.

### Output interpretation

-   HTTP status and server headers: what the server reports.
-   Potentially interesting paths: items requiring manual review.
-   Missing or risky headers: configuration observations, not always
    exploitable vulnerabilities.
-   Error messages/timeouts: may indicate an unavailable service,
    filtering, or connectivity issues.

Nikto sends active requests. Do not run it against a public website
without explicit permission.

------------------------------------------------------------------------

## 12. Nmap (Kali CLI)

**Purpose:** Discover hosts and assess open ports, service versions, and
other network details on authorized systems.

### Check installation

``` bash
command -v nmap
nmap --version
```

Install if needed:

``` bash
sudo apt update
sudo apt install nmap
```

### Safe local practice

Scan the Kali machine itself:

``` bash
nmap 127.0.0.1
```

Check selected ports on localhost:

``` bash
nmap -p 22,80,443 127.0.0.1
```

Service/version detection against localhost:

``` bash
nmap -sV 127.0.0.1
```

Save a text report:

``` bash
nmap -sV -oN nmap-report.txt 127.0.0.1
cat nmap-report.txt
```

### Interpret port states

-   `open`: a service accepted or responded to a connection attempt.
-   `closed`: the host responded, but no service is listening on that
    port.
-   `filtered`: Nmap could not determine the state because filtering or
    network behavior blocked the probe.
-   `open|filtered`: Nmap could not distinguish between open and
    filtered for that scan method.

A scan of `127.0.0.1` tests the local Kali machine, not another host.
Use other targets only when explicitly authorized.

------------------------------------------------------------------------

## 13. Nessus (Windows GUI)

**Purpose:** Vulnerability assessment through a browser-based interface.

### Open the local UI

1.  Install Nessus using the official Tenable installer.
2.  Open the local interface:

``` powershell
Start-Process "https://localhost:8834"
```

3.  Complete the activation and setup flow offered by your installed
    edition.
4.  If the browser shows a certificate warning, verify that the address
    is exactly `localhost:8834` before proceeding.

Official download page: https://www.tenable.com/downloads/nessus

### If the UI does not open

Check whether the local port responds:

``` powershell
Test-NetConnection -ComputerName localhost -Port 8834
```

If it fails: - Verify that Nessus is installed. - Open Windows Services
(`services.msc`) and look for the Nessus service. - Check whether the
service is running. - Restart the service only if appropriate and
permitted on your machine. - Retry `https://localhost:8834`.

### If setup shows Expert / Professional / Manager / Managed Scanner

Do not choose a paid edition or enter a random linking key. **Managed
Scanner** is intended to link to another Tenable management product. If
you intended to use Nessus Essentials but the option is missing, verify
the installer and registration flow at Tenable's official site. Do not
assume the current setup screen supports Essentials.

### Create a scan after activation

1.  Click **Scans → New Scan**.
2.  Choose **Basic Network Scan** for a general authorized assessment,
    or **Host Discovery** for discovery.
3.  Enter a name and the address of a machine you own or are authorized
    to assess.
4.  Save and launch the scan.
5.  Wait for completion and open the results.
6.  Inspect severity, affected host/port, plugin output/evidence,
    references, and remediation.

### Interpret severity

-   **Critical/High:** prioritize review and remediation.
-   **Medium/Low:** assess in context and follow the recommended fix.
-   **Informational:** useful details, not necessarily vulnerabilities.
-   **No findings:** does not prove the host is secure; confirm coverage
    and scan configuration.

A finding may be a false positive. Validate important issues before
reporting them as confirmed.

------------------------------------------------------------------------

## 14. Wireshark (Windows GUI and optional CLI)

**Purpose:** Capture and inspect network packets. Wireshark analyzes
traffic visible to the selected interface; it does not automatically see
every packet on a switched network.

### Open the UI

1.  Open Wireshark from the Start menu.
2.  Choose the active network interface (for example, Wi-Fi).
3.  Start capture.
4.  Generate ordinary traffic, such as opening `https://example.com`.
5.  Stop capture.
6.  Use display filters to inspect packets.
7.  Save the capture as `.pcapng` if needed.

### Useful display filters

``` text
dns
tcp
udp
tls
http
icmp
ip.addr == 192.0.2.10
tcp.port == 443
dns.qry.name == "example.com"
```

Replace the example IP with the address relevant to your own capture.

### Useful GUI analysis views

-   **Packet List:** one row per captured packet.
-   **Packet Details:** decoded protocol fields for the selected packet.
-   **Packet Bytes:** raw packet bytes.
-   **Statistics → Protocol Hierarchy:** protocol distribution.
-   **Statistics → Conversations:** endpoints exchanging traffic.

### Optional command-line check

If `tshark` is installed, try:

``` powershell
& "C:\Program Files\Wireshark\tshark.exe" --version
```

If the path differs, locate the installed Wireshark directory or use the
GUI. `tshark` is not always on Windows `PATH`.

### Interpretation notes

-   DNS query: asks for name information.
-   DNS response: contains an answer or an error/status.
-   TCP SYN, SYN-ACK, ACK: commonly represent the three-way handshake.
-   TLS traffic: encrypted application data is generally not readable as
    plain text without appropriate decryption material.
-   Capture limitations can hide traffic; an empty filter result does
    not prove no traffic occurred.

Capture only traffic you are authorized to inspect.

------------------------------------------------------------------------

## 15. Robtex (browser GUI)

**Purpose:** Look up public DNS, IP, reverse DNS, and network
relationships.

### Workflow

1.  Open https://robtex.com/
2.  Search `example.com` or an IP address you are authorized to
    investigate.
3.  Review available DNS records, IP information, reverse DNS, and
    ASN/network information.
4.  Follow an IP lookup only to inspect public metadata.
5.  Record the date and relevant fields for your lab report.

### Record meanings

-   `A`: IPv4 address.
-   `AAAA`: IPv6 address.
-   `MX`: mail server.
-   `NS`: nameserver.
-   `CNAME`: alias.
-   `TXT`: text record.
-   `PTR`: reverse DNS.
-   `ASN`: autonomous system number.

Robtex results can be stale or incomplete. An IP association does not
prove domain ownership or vulnerability.

------------------------------------------------------------------------

## 16. Armitage (Kali GUI)

**Purpose:** Legacy graphical interface for the Metasploit Framework.
Compatibility with current packages and Java versions can vary.

### Check installation

``` bash
command -v armitage
msfconsole --version
```

### Launch

``` bash
armitage
```

The launcher may start an RPC service automatically. A message such as:

``` text
Starting msfrpcd for you
MSGRPC ready
```

indicates that the RPC service started. Java reflective-access warnings
may be compatibility warnings rather than fatal errors.

### Common database warning

If you see a message such as:

``` text
No database support: No database YAML file
```

Armitage reports that database support is not configured. Some features
may still run, but stored host/service information may not work as
expected.

Check PostgreSQL:

``` bash
sudo systemctl status postgresql
```

If it is installed but inactive:

``` bash
sudo systemctl start postgresql
sudo systemctl status postgresql
```

Do not assume this alone completes Metasploit database configuration;
use the current Kali/Metasploit setup documentation if configuration is
still missing.

### Safe practical scope

For an initial lab: 1. Confirm the GUI opens. 2. Confirm the framework
connection. 3. Explore the host workspace and module browser. 4. Use
only an isolated, authorized lab target for any further testing. 5. Do
not launch exploits against public or unapproved systems.

Armitage is a legacy tool; if it fails with the current Metasploit
installation, document the exact error rather than repeatedly starting
services.

------------------------------------------------------------------------

## 17. Quick viva revision

  -----------------------------------------------------------------------
  Question                            Short answer
  ----------------------------------- -----------------------------------
  What is OSINT?                      Intelligence gathered from publicly
                                      available sources.

  What is DNS?                        A naming system that maps domain
                                      names to records such as IP
                                      addresses.

  What is a vulnerability scanner?    A tool that checks systems for
                                      known weaknesses and security
                                      conditions.

  What is a false positive?           A reported issue that is not
                                      actually present or applicable.

  What is the difference between Nmap Nmap focuses on
  and Nessus?                         network/port/service discovery;
                                      Nessus focuses on vulnerability
                                      assessment.

  What is the difference between      Maltego visualizes relationships
  Maltego and Robtex?                 through transforms; Robtex provides
                                      DNS/IP/network lookup information.

  What is a Wireshark display filter? A filter that controls which
                                      captured packets are shown.

  What does an Nmap `filtered` port   The scan could not determine
  mean?                               whether the port is open because
                                      probes were blocked or affected by
                                      filtering.

  What is a Metasploit payload?       Code or an action delivered by an
                                      exploit to perform a particular
                                      operation.

  Why do we need authorization?       Active tests can affect
                                      availability, data, and security
                                      controls.
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 18. Suggested lab record template

Use this template for each experiment:

``` text
Experiment:
Aim:
Tool and version:
Environment:
Authorized target:
Procedure:
Commands or GUI steps:
Observed output:
Interpretation:
Result:
Limitations:
Precautions:
```

### Git usage

Save this file as `ethical-hacking-lab-commands.md`, then add it to your
repository:

``` bash
git add ethical-hacking-lab-commands.md
git commit -m "Add ethical hacking lab command guide"
git push
```

If your branch has no upstream configured, Git may ask you to set one;
use the repository's actual branch name rather than guessing.

------------------------------------------------------------------------

## Responsible-use checklist

-   Confirm the target is yours or explicitly authorized.
-   Verify the actual network interface and subnet before discovery.
-   Prefer localhost or an isolated lab VM for active scans.
-   Do not expose Nessus or Metasploit RPC services to untrusted
    networks.
-   Treat results as evidence to validate, not automatic proof.
-   Keep sensitive packet captures, scan reports, and discovered
    personal data out of public Git repositories.
