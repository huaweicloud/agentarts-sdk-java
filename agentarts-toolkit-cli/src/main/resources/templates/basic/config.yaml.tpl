default_agent: {{ name }}
agents:
  {{ name }}:
    base:
      name: {{ name }}
      entrypoint: com.example.Agent
      region: {{ region }}
      language: java17
      base_image: eclipse-temurin:17-jre
      dependency_file: pom.xml
      platform: linux/amd64
      container_runtime: docker
    swr_config:
      organization: {{ swr_org }}
      repository: {{ swr_repo }}
      organization_auto_create: true
      repository_auto_create: true
    runtime:
      arch: arm64
      agent_gateway_id: null
      invoke_config:
        protocol: HTTP
        port: 8080
        file_transfer_config:
          enabled: false
        url_match_type: ACCURATE_MATCH
      network_config:
        network_mode: PUBLIC
        vpc_config:
          vpc_id: null
          subnet_id: null
          security_group_id: []
      identity_configuration:
        authorizer_type: IAM
        authorizer_configuration:
          custom_jwt:
            discovery_url: null
            allowed_audience: []
            allowed_clients: []
            allowed_scopes: []
          key_auth:
            api_keys: []
      observability:
        tracing:
          enabled: false
        metrics:
          enabled: false
        logs:
          enabled: false
      artifact_source:
        url: null
        swr_instance_id: null
        commands: []
      storage_config:
        sfs_turbo:
          sfs_turbo_id: null  # UUID format, required when using SFS Turbo
          sfs_path: null
          mount_path: null    # required when using SFS Turbo
          read_only: false
      environment_variables: {}
      tags: {}
