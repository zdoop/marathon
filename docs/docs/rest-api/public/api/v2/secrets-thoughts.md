### Questions
- Are we willing/able/required to support file based secrets that are onlz visible to a specific pod container? Is that even technically possible? (Background: we need to support env vars specific to pod containers). 

Currently, Marathon's API requires to define secrets and refer to them in the env var section:

```
{
  "id": "/status-quo",
  "secrets": {
    "secret1": { "source": "/db/password" }
  },
  "env": {
    "PASSWORD": { "secret": "/db/password" }
  }
}
```

The is induced by the idea that a user specifies a list of environment variables with either a designated value, or a reference to a secret and makes it easy to see the list of vars to be expected in one go.

In order to support file based secrets, there are three options. The first two are in alignment with the current approach, the last one requires adjusting teh API for the sake of resulting simplicity.

## Using the fetch/artifacts block
The `fetch` property was introduced to define a list of URIs that shall be directed to the Mesos fetcher. For pods, this field is called `artifacts`. Using the `fetch` property for secret files would be rather straightforward for app definitions. However, it would require us to introduce an additional `artifacts` field (or something similar to a `volumeMount`) on pod container level, if we want to allow defining file based secrets that are only accessible from one container. Otherwise, all files fetched are placed in designated folders inside the sandbox; there is no distinction for the visibility per pod container. Therefore, I consider using fetch semantics as a suboptimal way to go.

```
{
  "id": "/fbs-via-fetch",
  "secrets": {
    "db-password": { "source": "/db/password" }
  },
  "artifacts": [
    { "secret-ref": "/db-password", "destPath": "secrets/db-password" }
  ],
  "container": {
    "type": "MESOS",
    "artifacts": [
      { "secret-ref": "/db-password", "destPath": "secrets/db-password" }
    ],
  }
}
```

## Using volumes
The `volumes` field is currently used to define volumes that can be mounted as either external volumes, local persistent volumes, or docker volumes. For a pod, volumes are defined on pod level and the usage as mount points on container level:

```
{
  "id": "/fbs-via-volumes",
  "secrets": {
    "db-password": { "source": "/db/password" }
  },
  "volumes": [
    {
      "name": "db-password", // needed?
      "secret-ref": "db-password"
    }
  ],
  "container": {
    "type": "MESOS",
    "volumeMounts": [
      {
        "name": "db-password",
        "mountPath": "/secrets/db-password",
        "readOnly": true // allowed?
      }
    ]
  }
}
```

There are several downsides of using volumes for file based secrets as well:

1. In order to specify a file based secret for a pod container via volumes, the user has to edit the pod spec in three places: `secrets`, `volumes`, `volumeMounts`. This is presumed to be tedious for the UI, where the user likely wants to specify the secret in proximity of a pod container, and it is surely tedious for someone editing a JSON since correctness cannot be assured simply by validating the form, but requires evaluating valid references.
1. The general semantic of volumes is to define a containerPath under which a volume is mounted. File based secrets require the specification of a file, so the semantic common to external, local and docker volumes, namely `containerPath` designating a directory, has to be widened to designating a file in case of a secrets related volume.
1. If we don't plan to support file based secrets per pod container (but only secrets that are visible to all pod containers), this doesn't need volume mounts on container level. However, this somehow breaks the semantics: For a normal volume, the user needs to specify a volume and a volumeMount, otherwise the volume won't be visible to the container. For a file based, secret, she wouldn't have to define a volumeMount, but the file would still be visible.
1. the internal handling of volumes (PersistentVolume, ExternalVolume, DockerVolume) is already very cluttered. Adding more complexity here doesn't seem a good choice since the semantics are different for every type of volume. Adding a SecretVolume should be considered adding tech debt.

## Adjusting secrets spec

There is an alternative to the above that requires a little extra work to adjust the existing API, but allows for a simpler configuration of secrets:

Simple example of an app/pod declaring a file based secret and and env var secret 
```
{
  "id": "/fbs-plain",
  "secrets": [
    {
      "source": "/password/some-api",
      "file": {
        "path": "secrets/some-api-password"
      }
    },
    {
      "source": "/password/other-api",
      "env": {
        "name": "OTHER_PASSWORD"
      }
    }
  ]
}
```
