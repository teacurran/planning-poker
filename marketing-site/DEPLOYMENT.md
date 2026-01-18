# Marketing Site Deployment Guide

This guide provides step-by-step instructions for deploying the Planning Poker marketing site to production.

## Pre-Deployment Checklist

Before deploying, ensure the following items are completed:

- [ ] All content reviewed and approved (landing, pricing, demo, blog, legal)
- [ ] Pricing matches main application exactly (verify against `frontend/src/utils/subscriptionUtils.ts`)
- [ ] Privacy policy and terms of service reviewed by legal counsel
- [ ] Contact form configured (Formspree form ID or custom endpoint)
- [ ] Analytics tracking code added (Google Analytics or Plausible)
- [ ] Demo video uploaded to YouTube (if using video option)
- [ ] Favicon and brand assets added to `public/assets/`
- [ ] Site URL updated in `astro.config.mjs`
- [ ] All links tested (internal navigation, external links to app)

## Build Verification

### Local Build Test

```bash
cd marketing-site

# Install dependencies (if not already installed)
npm install

# Build the site
npm run build

# Preview the production build
npm run preview
```

Visit `http://localhost:4321` and verify:
- All pages load without errors
- Navigation works correctly
- Images and assets load
- Forms submit correctly (if using custom backend)
- Mobile responsiveness (test with browser DevTools)

### Performance Test

Use [Google PageSpeed Insights](https://pagespeed.web.dev/) to test the preview build:

```bash
# Make preview accessible from network
npm run preview -- --host

# Test with PageSpeed Insights using your local IP
# Target: Score ≥90 for both mobile and desktop
```

## Deployment Options

### Option 1: Netlify (Recommended)

**Pros:**
- Easy continuous deployment from Git
- Automatic HTTPS with Let's Encrypt
- Built-in CDN
- Form handling without Formspree

**Steps:**

1. **Push to Git Repository**
   ```bash
   git add marketing-site/
   git commit -m "feat: add marketing site for production launch"
   git push origin main
   ```

2. **Connect to Netlify**
   - Visit https://app.netlify.com/
   - Click "Add new site" → "Import an existing project"
   - Connect your Git repository (GitHub, GitLab, or Bitbucket)

3. **Configure Build Settings**
   - **Base directory:** `marketing-site`
   - **Build command:** `npm run build`
   - **Publish directory:** `marketing-site/dist`
   - **Node version:** 18 or higher (set in `netlify.toml` or environment)

4. **Add Environment Variables** (if needed)
   - Go to Site settings → Environment variables
   - Add any required variables (e.g., analytics tracking ID)

5. **Configure Custom Domain**
   - Go to Domain settings → Add custom domain
   - Add `planningpoker.example.com`
   - Update DNS records as instructed by Netlify:
     - **A record:** `@` → Netlify load balancer IP
     - **CNAME:** `www` → `<your-site>.netlify.app`

6. **Enable HTTPS**
   - Netlify automatically provisions SSL certificate via Let's Encrypt
   - Verify HTTPS is working: visit `https://planningpoker.example.com`

7. **Configure Netlify Forms** (optional, if not using Formspree)
   - Update `src/components/ContactForm.astro`:
     ```html
     <form name="contact" method="POST" data-netlify="true">
       <input type="hidden" name="form-name" value="contact">
       <!-- rest of form fields -->
     </form>
     ```
   - Forms submissions will appear in Netlify dashboard

**Netlify Configuration File** (optional):

Create `marketing-site/netlify.toml`:

```toml
[build]
  base = "marketing-site"
  command = "npm run build"
  publish = "dist"

[[headers]]
  for = "/*"
  [headers.values]
    X-Frame-Options = "DENY"
    X-Content-Type-Options = "nosniff"
    Referrer-Policy = "strict-origin-when-cross-origin"
    Permissions-Policy = "geolocation=(), microphone=(), camera=()"

[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200
```

### Option 2: Vercel

**Pros:**
- Excellent performance
- Automatic HTTPS
- Global CDN
- Zero-config deployments

**Steps:**

1. **Push to Git Repository** (same as Netlify)

2. **Import to Vercel**
   - Visit https://vercel.com/
   - Click "Add New" → "Project"
   - Import your Git repository

3. **Configure Project**
   - **Framework Preset:** Astro
   - **Root Directory:** `marketing-site`
   - **Build Command:** `npm run build`
   - **Output Directory:** `dist`

4. **Add Custom Domain**
   - Go to Project settings → Domains
   - Add `planningpoker.example.com`
   - Update DNS records as instructed

5. **Deploy**
   - Vercel automatically deploys on every push to main branch

### Option 3: Cloudflare Pages

**Pros:**
- Extremely fast global CDN
- Built-in DDoS protection
- Free SSL
- Integrated with Cloudflare DNS

**Steps:**

1. **Push to Git Repository** (same as Netlify)

2. **Create Pages Project**
   - Visit https://dash.cloudflare.com/
   - Go to Pages → Create a project
   - Connect your Git repository

3. **Configure Build**
   - **Production branch:** main
   - **Build command:** `npm run build`
   - **Build output directory:** `dist`
   - **Root directory:** `marketing-site`

4. **Add Custom Domain**
   - Go to Custom domains → Set up a custom domain
   - Add `planningpoker.example.com`
   - Update DNS (automatic if using Cloudflare DNS)

5. **Deploy**
   - Cloudflare Pages automatically deploys on push

### Option 4: AWS S3 + CloudFront

**Pros:**
- Full control over infrastructure
- Scalable and reliable
- Integration with other AWS services

**Steps:**

1. **Build Site Locally**
   ```bash
   npm run build
   ```

2. **Create S3 Bucket**
   ```bash
   aws s3 mb s3://planningpoker-marketing-site
   ```

3. **Configure Bucket for Static Website Hosting**
   ```bash
   aws s3 website s3://planningpoker-marketing-site \
     --index-document index.html \
     --error-document 404.html
   ```

4. **Set Bucket Policy** (public read access)
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "PublicReadGetObject",
         "Effect": "Allow",
         "Principal": "*",
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::planningpoker-marketing-site/*"
       }
     ]
   }
   ```

5. **Upload Site to S3**
   ```bash
   aws s3 sync dist/ s3://planningpoker-marketing-site/ \
     --delete \
     --cache-control "public, max-age=3600"
   ```

6. **Create CloudFront Distribution**
   - Origin: S3 bucket website endpoint
   - Viewer Protocol Policy: Redirect HTTP to HTTPS
   - Compress Objects Automatically: Yes
   - Alternate Domain Names (CNAMEs): `planningpoker.example.com`
   - SSL Certificate: Request or import ACM certificate

7. **Update DNS**
   - Create CNAME record: `planningpoker.example.com` → CloudFront distribution domain

8. **Invalidate CloudFront Cache** (after updates)
   ```bash
   aws cloudfront create-invalidation \
     --distribution-id DISTRIBUTION_ID \
     --paths "/*"
   ```

## Post-Deployment Verification

After deploying, verify the following:

### 1. Site Accessibility

- [ ] Site loads at `https://planningpoker.example.com`
- [ ] All pages accessible (/, /pricing, /demo, /blog, /privacy, /terms)
- [ ] No 404 errors for assets (images, fonts, CSS)

### 2. SSL/HTTPS

- [ ] HTTPS enforced (HTTP redirects to HTTPS)
- [ ] SSL certificate valid (not expired)
- [ ] No mixed content warnings
- [ ] Test with SSL Labs: https://www.ssllabs.com/ssltest/

### 3. Performance

- [ ] PageSpeed Insights score ≥90 (mobile and desktop)
  - Test: https://pagespeed.web.dev/
- [ ] Lighthouse audit passing
  - Performance: ≥90
  - Accessibility: ≥90
  - Best Practices: ≥90
  - SEO: ≥90

### 4. SEO

- [ ] Sitemap accessible at `/sitemap.xml`
- [ ] Robots.txt accessible at `/robots.txt`
- [ ] Meta tags present on all pages (view page source)
- [ ] Open Graph tags for social sharing
- [ ] Submit sitemap to Google Search Console

### 5. Functionality

- [ ] Navigation menu works on all pages
- [ ] Internal links navigate correctly
- [ ] External links open in new tab (app links)
- [ ] Contact form submits successfully
- [ ] Footer links work (privacy, terms, contact)
- [ ] Mobile menu works (if applicable)

### 6. Mobile Responsiveness

Test on multiple viewport sizes:
- [ ] Mobile (375px width)
- [ ] Tablet (768px width)
- [ ] Desktop (1280px width)

### 7. Cross-Browser Testing

- [ ] Chrome (latest)
- [ ] Firefox (latest)
- [ ] Safari (latest)
- [ ] Edge (latest)

### 8. Analytics

- [ ] Analytics tracking code firing
- [ ] Pageviews recorded in analytics dashboard
- [ ] Conversion events tracking (sign-up clicks, etc.)

## Continuous Deployment

### Automatic Deployments

For Netlify, Vercel, or Cloudflare Pages:
- Every push to `main` branch automatically triggers a deployment
- Preview deployments created for pull requests
- Rollback available from deployment history

### Manual Deployments

For AWS S3 or custom hosting:

1. **Build locally:**
   ```bash
   npm run build
   ```

2. **Deploy:**
   ```bash
   # AWS S3
   aws s3 sync dist/ s3://planningpoker-marketing-site/ --delete

   # rsync to server
   rsync -avz --delete dist/ user@server:/var/www/html/

   # Invalidate CloudFront cache (if using)
   aws cloudfront create-invalidation --distribution-id DISTID --paths "/*"
   ```

## Rollback Procedure

### Netlify/Vercel/Cloudflare Pages

1. Go to Deployments page in dashboard
2. Find previous successful deployment
3. Click "Publish deploy" or "Promote to production"

### AWS S3

1. Enable versioning on S3 bucket (if not already enabled)
2. Restore previous version of objects
3. Invalidate CloudFront cache

## Monitoring

### Uptime Monitoring

Set up uptime monitoring with:
- **UptimeRobot** (free): https://uptimerobot.com/
- **Pingdom** (paid): https://www.pingdom.com/
- **AWS CloudWatch** (if using AWS)

**Recommended checks:**
- HTTPS endpoint check every 5 minutes
- Alert if down for >2 minutes
- Alert email: ops@planningpoker.example.com

### Analytics Monitoring

- Review traffic weekly in Google Analytics or Plausible
- Monitor conversion rates (sign-up clicks from marketing site)
- Track top landing pages and traffic sources

### Performance Monitoring

- Run PageSpeed Insights monthly
- Monitor Core Web Vitals in Google Search Console
- Address any performance regressions promptly

## Troubleshooting

### Issue: 404 Errors After Deployment

**Solution:** Configure server to serve `index.html` for all routes (SPA fallback)
- **Netlify:** Add `_redirects` file or use `netlify.toml`
- **Vercel:** Automatically handled by Vercel
- **S3:** Configure custom error document to `index.html`

### Issue: Assets Not Loading (CORS Errors)

**Solution:** Ensure proper CORS headers on asset requests
- Check CloudFront or CDN CORS configuration
- Verify S3 bucket CORS policy

### Issue: Slow Load Times

**Solutions:**
- Enable compression (gzip/brotli) on server
- Optimize images (convert to WebP, lazy loading)
- Minimize CSS/JS bundle size
- Use CDN for asset delivery

### Issue: Contact Form Not Working

**Solutions:**
- **Formspree:** Verify form ID is correct
- **Netlify Forms:** Ensure `data-netlify="true"` attribute present
- **Custom Backend:** Check API endpoint is accessible and CORS enabled

## Support

For deployment issues:
- **Email:** devops@planningpoker.example.com
- **Slack:** #infrastructure channel
- **Documentation:** See main project `docs/deployment/`

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-01-18 | Initial marketing site deployment guide | DevOps Team |
