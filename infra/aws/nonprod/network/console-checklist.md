# NonProd VPC Console Checklist

Use this as the historical checklist for the initial network created manually in the AWS Console. NAT Gateway, private route and S3 Gateway Endpoint were added after this baseline and are tracked in `infra/aws/foundation-status.md`.

## Preflight

- Account: `MeetingMind-NonProd`
- Permission set: `AWSAdministratorAccess`
- Region: Seoul `ap-northeast-2`
- Scope: initial VPC baseline only. Do not create NAT Gateway, ECS, RDS, ElastiCache, VPC endpoints, or EC2 instances in this step.

## VPC

Create one VPC:

| Field | Value |
| --- | --- |
| Name | `meetingmind-nonprod-vpc` |
| IPv4 CIDR | `10.20.0.0/16` |
| IPv6 | None |
| DNS hostnames | Enabled |
| DNS resolution | Enabled |

Tags:

| Key | Value |
| --- | --- |
| Project | `MeetingMind` |
| Environment | `nonprod` |
| ManagedBy | `console` |
| Stage | `network-baseline` |

## Subnets

Select two AZs available in the account. AZ letters can map differently per AWS account, so treat them as AZ 1 and AZ 2 rather than relying on another account's physical mapping.

| Name | Tier | CIDR | Public IPv4 auto-assign |
| --- | --- | --- | --- |
| `meetingmind-nonprod-public-1` | public | `10.20.0.0/24` | Enabled |
| `meetingmind-nonprod-public-2` | public | `10.20.1.0/24` | Enabled |
| `meetingmind-nonprod-private-1` | private | `10.20.16.0/20` | Disabled |
| `meetingmind-nonprod-private-2` | private | `10.20.32.0/20` | Disabled |
| `meetingmind-nonprod-data-1` | data | `10.20.48.0/24` | Disabled |
| `meetingmind-nonprod-data-2` | data | `10.20.49.0/24` | Disabled |

Add `Tier=public|private|data` tags to each subnet.

## Internet Gateway

Create and attach:

| Field | Value |
| --- | --- |
| Name | `meetingmind-nonprod-igw` |
| Attached VPC | `meetingmind-nonprod-vpc` |

## Route Tables

Create route tables:

| Name | Associated subnets | Routes |
| --- | --- | --- |
| `meetingmind-nonprod-public-rt` | both public subnets | local + `0.0.0.0/0 -> meetingmind-nonprod-igw` |
| `meetingmind-nonprod-private-1-rt` | `private-1` | local only |
| `meetingmind-nonprod-private-2-rt` | `private-2` | local only |
| `meetingmind-nonprod-data-1-rt` | `data-1` | local only |
| `meetingmind-nonprod-data-2-rt` | `data-2` | local only |

## Validation

- VPC CIDR is `10.20.0.0/16`.
- There are exactly 6 subnets across 2 AZs.
- Only public subnets have auto-assign public IPv4 enabled.
- Only the public route table has `0.0.0.0/0` to the internet gateway.
- Private and data route tables have no NAT Gateway, internet gateway, or VPC endpoint routes.
- No EC2, NAT Gateway, ECS, RDS, ElastiCache, or VPC endpoint was created during this initial step.
