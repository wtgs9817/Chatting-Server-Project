# Chatting-Server-Project
<br />

**main 브랜치** - 다중 서버 환경에서 Redis Pub/Sub를 도입하여 서버 간 메시지 동기화. 비동기 배치 저장 (5분마다 버퍼 확인 + 각 서버마다 200개 이상 시 일괄 저장)

<br />

**test01 브랜치** - Redis(Pub/Sub) 제외
(목적: 다중 서버 환경에서 서버 간 메시지 전달 실패 확인(Redis Pub/Sub 도입 이유 증명)

<br />

**test02 브랜치** - 동기 저장 방식
(목적: 비동기 배치 저장 대비 DB I/O 부하 비교)

<br /> 

**test03 브랜치** - 단일 채팅 서버 
(목적: 다수의 사용자가 이용할 때 다중 서버 환경의 확장성 증명)

<br /> <br />
# 👍 개선점



<br /> <br />
# 프로젝트 아키텍처
<img width="3077" height="2081" alt="채팅프로젝트_아키텍처_최종" src="https://github.com/user-attachments/assets/a000c1db-f944-4cf9-a277-736cf9699524" />

<br /> <br />
# DB
<br /> 
https://www.erdcloud.com/d/zzPHfL5dZrbtexz6J
<br /><br />
<img width="1212" height="597" alt="image" src="https://github.com/user-attachments/assets/fa0e7383-36e8-4133-b414-447d5b75efc0" />
