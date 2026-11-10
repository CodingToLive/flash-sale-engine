# Oracle Cloud VM setup (one-time, manual)

This is a manual checklist, not something CI runs. Do it once; after that,
`.github/workflows/deploy.yml` only pulls fresh images and restarts the stack.

## 1. Create the instance

- Oracle Cloud Console → Compute → Instances → Create instance.
- Shape: `VM.Standard.A1.Flex` (Ampere ARM), 4 OCPU / 24 GB RAM - the max the
  "Always Free" tier allows, and enough to run the whole stack (Postgres, Redis,
  RabbitMQ, 5 services, Caddy) continuously for $0.
- Image: Ubuntu 22.04 (aarch64).
- Generate an SSH keypair during creation (or supply your own public key) so you
  can log in as the default `ubuntu` user.
- Note the instance's public IP - it's static on Oracle's free tier, no dynamic
  DNS updater needed.

## 2. Open the required ports (both places - this is the common gotcha)

- **VCN Security List / Network Security Group**: add ingress rules for TCP
  80, 443, and 22 from `0.0.0.0/0`.
- **Instance's own firewall**: Ubuntu's default image usually has no local
  firewall blocking these, but if `ufw` is active:
  ```bash
  sudo ufw allow 22/tcp
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
  ```

## 3. Install Docker Engine + Compose plugin

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
# log out/in (or `newgrp docker`) for the group change to take effect
docker compose version
```

## 4. Create a dedicated deploy user + SSH key for GitHub Actions

Using a separate `deploy` user (rather than handing CI your personal/root key)
keeps the blast radius small if the key ever leaks.

```bash
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy
sudo mkdir -p /home/deploy/.ssh

# On your own machine, generate a dedicated keypair:
ssh-keygen -t ed25519 -C "github-actions-deploy" -f flash-sale-deploy-key -N ""

# Copy the PUBLIC half onto the VM:
cat flash-sale-deploy-key.pub | sudo tee -a /home/deploy/.ssh/authorized_keys
sudo chown -R deploy:deploy /home/deploy/.ssh
sudo chmod 700 /home/deploy/.ssh
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

Store the PRIVATE half (`flash-sale-deploy-key`) as the GitHub repo secret
`ORACLE_VM_SSH_KEY`. Also add `ORACLE_VM_HOST` (the public IP or domain) and
`ORACLE_VM_USER=deploy` as repo secrets.

## 5. Copy the non-source files onto the VM

These intentionally never live in the git repo (secrets, server-specific config):

```bash
sudo mkdir -p /opt/flash-sale-system
sudo chown deploy:deploy /opt/flash-sale-system

scp docker-compose.prod.yml deploy@<VM_IP>:/opt/flash-sale-system/
scp infra/Caddyfile deploy@<VM_IP>:/opt/flash-sale-system/Caddyfile
scp -r frontend deploy@<VM_IP>:/opt/flash-sale-system/

# Hand-write a real .env on the VM from .env.example - do NOT scp a local .env
# that might contain dev defaults.
ssh deploy@<VM_IP> 'nano /opt/flash-sale-system/.env'
```

`.env` on the VM needs at least: `DB_PASSWORD`, `ADMIN_SECRET`, `GHCR_NAMESPACE`
(your lowercase GitHub username/org), `CADDY_ADDRESS` (your DuckDNS domain),
and - only if you opt into real email - `EMAIL_ENABLED=true` plus the `SMTP_*`
variables for a transactional provider.

## 6. Point a free domain at the VM

- Register a subdomain at [duckdns.org](https://www.duckdns.org) (e.g.
  `flash-sale-demo.duckdns.org`) pointed at the VM's public IP.
- Since the IP is static on Oracle's free tier, you generally don't need
  DuckDNS's dynamic-update cron job - set it once and leave it.

## 7. First manual deploy (before letting CI automate it)

```bash
ssh deploy@<VM_IP>
cd /opt/flash-sale-system
docker login ghcr.io -u <your-github-username>   # use a GitHub PAT with read:packages
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

Visit `https://<your-subdomain>.duckdns.org` - Caddy auto-provisions a real
Let's Encrypt certificate the moment it sees traffic for a public domain name.

## 8. Let GitHub Actions take over

Once step 7 works manually, push to `main`: `.github/workflows/deploy.yml`
builds fresh images, pushes them to GHCR, then SSHes in and re-runs the same
`pull && up -d` you just did by hand.

## 9. Light hardening (optional but recommended)

```bash
# Disable SSH password auth (key-only)
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo systemctl restart sshd

# Keep the OS patched automatically
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure --priority=low unattended-upgrades

# Free-tier boot volume is limited - prune old images/layers weekly
( crontab -l 2>/dev/null; echo "0 4 * * 0 docker system prune -f" ) | crontab -
```
