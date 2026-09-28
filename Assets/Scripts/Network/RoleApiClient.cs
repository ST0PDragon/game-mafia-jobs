using System;
using System.Collections;
using UnityEngine;
using UnityEngine.Networking;

namespace MafiaGame.Network
{
    // JsonUtility는 공개 필드를 읽는다. 필드 이름은 서버 JSON 응답과 동일해야 한다.
    [Serializable]
    public class RoleData
    {
        public string code;
        public string name;
        public string faction;
    }

    [Serializable]
    public class RoleListResponse
    {
        public RoleData[] roles;
    }

    [Serializable]
    public class AbilityData
    {
        public string actionCode;
        // -1이면 게임 전체 횟수 제한이 없다. 같은 밤 중복 제출 가능 여부와는 다르다.
        public int remainingUses;
        public string phase;
    }

    [Serializable]
    public class MyRoleResponse
    {
        public long gamesId;
        public RoleData role;
        public bool alive;
        public AbilityData[] abilities;
        // 해적에게만 다른 해적의 playerId가 들어간다. userId가 아니다.
        public long[] allies;
    }

    [Serializable]
    public class RoomReportData
    {
        public long id;
        public int roundNumber;
        public string type;
        public string message;
    }

    [Serializable]
    public class ReportsResponse
    {
        public RoomReportData[] reports;
    }

    [Serializable]
    public class ActionRequest
    {
        // 전송 결과를 모를 때 같은 요청을 재시도할 수 있도록 UUID 문자열을 보관한다.
        public string requestId;
        public string actionCode;
        public long targetPlayerId;
    }

    [Serializable]
    public class ActionResponse
    {
        public long actionId;
        public long gamesId;
        public string status;
        public string actionCode;
        public long targetPlayerId;
    }

    /// <summary>
    /// 온라인 게임 직업 API 전용 클라이언트. 호출자는 StartCoroutine으로 각 메서드를 실행한다.
    /// 기존 로컬 GameManager의 판정 로직은 호출하지 않는다.
    /// </summary>
    public class RoleApiClient : MonoBehaviour
    {
        // 에디터 로컬 실행 주소. 배포 시 Inspector에서 HTTPS 서버 주소로 바꾼다.
        [SerializeField] private string baseUrl = "http://localhost:8080/api/v1";

        /// <summary>공개 직업 목록. faction을 비우면 모든 진영을 가져온다.</summary>
        public IEnumerator GetRoles(string faction, Action<RoleListResponse> onSuccess,
                                    Action<long, string> onError)
        {
            string query = string.IsNullOrEmpty(faction)
                ? string.Empty
                : "?faction=" + UnityWebRequest.EscapeURL(faction);
            return Send(UnityWebRequest.Get(baseUrl + "/roles" + query),
                        null, onSuccess, onError);
        }

        /// <summary>JWT의 사용자에게 배정된 직업만 가져온다.</summary>
        public IEnumerator GetMyRole(long gamesId, string accessToken,
                                     Action<MyRoleResponse> onSuccess,
                                     Action<long, string> onError)
        {
            return Send(UnityWebRequest.Get(baseUrl + "/games/" + gamesId + "/me/role"),
                        accessToken, onSuccess, onError);
        }

        /// <summary>본인에게 공개된 밤 조사 결과와 공개 사망 알림을 가져온다.</summary>
        public IEnumerator GetMyReports(long gamesId, string accessToken,
                                        Action<ReportsResponse> onSuccess,
                                        Action<long, string> onError)
        {
            return Send(UnityWebRequest.Get(baseUrl + "/games/" + gamesId + "/me/reports"),
                        accessToken, onSuccess, onError);
        }

        /// <summary>
        /// 낮 또는 밤 행동을 제출한다. 통신 오류 후 재시도할 때는 같은 requestId와 내용을 전달한다.
        /// targetPlayerId는 계정 userId가 아니라 게임 안의 playerId다.
        /// </summary>
        public IEnumerator SubmitAction(long gamesId, string accessToken,
                                        string requestId, string actionCode,
                                        long targetPlayerId,
                                        Action<ActionResponse> onSuccess,
                                        Action<long, string> onError)
        {
            var body = new ActionRequest
            {
                requestId = requestId,
                actionCode = actionCode,
                targetPlayerId = targetPlayerId
            };
            string json = JsonUtility.ToJson(body);
            UnityWebRequest request = UnityWebRequest.Post(
                baseUrl + "/games/" + gamesId + "/actions", json, "application/json");
            return Send(request, accessToken, onSuccess, onError);
        }

        private IEnumerator Send<T>(UnityWebRequest request, string accessToken,
                                    Action<T> onSuccess, Action<long, string> onError)
        {
            // request를 여기서 해제하므로 호출자는 UnityWebRequest를 별도로 Dispose하지 않는다.
            using (request)
            {
                if (!string.IsNullOrEmpty(accessToken))
                {
                    // accessToken에는 "Bearer " 접두사를 넣지 않는다.
                    request.SetRequestHeader("Authorization", "Bearer " + accessToken);
                }

                yield return request.SendWebRequest();

                if (request.result != UnityWebRequest.Result.Success)
                {
                    // HTTP 오류는 상태 코드가 오고, 연결 실패는 responseCode가 0일 수 있다.
                    string body = request.downloadHandler != null
                        ? request.downloadHandler.text : request.error;
                    onError?.Invoke(request.responseCode, body);
                    yield break;
                }

                onSuccess?.Invoke(JsonUtility.FromJson<T>(request.downloadHandler.text));
            }
        }
    }
}
