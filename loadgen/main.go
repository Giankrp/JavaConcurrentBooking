package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"sort"
	"sync"
	"time"
)

const conflictHTTP = 409

type idOnly struct {
	ID int64 `json:"id"`
}

type raceResult struct {
	code     int
	err      error
	duration time.Duration
}

func main() {
	baseURL := flag.String("url", "http://localhost:8080", "base URL of the running API")
	racers := flag.Int("n", 50, "number of concurrent clients")
	startFlag := flag.String("start", "", "booking start time (RFC3339; default: tomorrow 10:00 UTC)")
	endFlag := flag.String("end", "", "booking end time (RFC3339; default: start + 1h)")
	flag.Parse()

	start, end := interval(*startFlag, *endFlag)

	client := &http.Client{
		Transport: &http.Transport{
			MaxIdleConns:        *racers,
			MaxIdleConnsPerHost: *racers,
		},
		Timeout: 30 * time.Second,
	}

	userID, err := createID(client, *baseURL+"/api/users", map[string]string{
		"name":  "Load Generator",
		"email": fmt.Sprintf("loadgen-%d@example.com", time.Now().UnixMilli()),
	})
	exitOnError("create user", err)

	resourceID, err := createID(client, *baseURL+"/api/resources", map[string]string{
		"name": fmt.Sprintf("Load Room %d", time.Now().UnixMilli()),
	})
	exitOnError("create resource", err)

	fmt.Printf("user=%d resource=%d interval=[%s, %s) racers=%d\n",
		userID, resourceID, start.Format(time.RFC3339), end.Format(time.RFC3339), *racers)

	payload, err := json.Marshal(map[string]any{
		"userId":     userID,
		"resourceId": resourceID,
		"startTime":  start.Format(time.RFC3339),
		"endTime":    end.Format(time.RFC3339),
	})
	exitOnError("marshal booking", err)

	results := race(client, *racers, *baseURL+"/api/bookings", payload)

	successes, conflicts, errors := 0, 0, 0
	var latencies []float64
	for _, r := range results {
		switch {
		case r.err != nil:
			errors++
			fmt.Printf("request error: %v\n", r.err)
		case r.code == http.StatusCreated:
			successes++
		case r.code == conflictHTTP:
			conflicts++
		default:
			errors++
			fmt.Printf("unexpected status: %d\n", r.code)
		}
		if r.err == nil {
			latencies = append(latencies, r.duration.Seconds()*1000)
		}
	}

	sort.Float64s(latencies)
	p50, p95, max := percentile(latencies, 50), percentile(latencies, 95), percentile(latencies, 100)

	fmt.Printf("201: %d  409: %d  other: %d\n", successes, conflicts, errors)
	fmt.Printf("latency ms: p50=%.1f p95=%.1f max=%.1f\n", p50, p95, max)

	if successes != 1 {
		fmt.Printf("INVARIANT VIOLATED: expected exactly 1 success, got %d\n", successes)
		os.Exit(1)
	}
	fmt.Println("INVARIANT OK: exactly one booking won the interval")
}

func interval(startFlag, endFlag string) (time.Time, time.Time) {
	var start time.Time
	if startFlag != "" {
		var err error
		start, err = time.Parse(time.RFC3339, startFlag)
		exitOnError("parse -start", err)
	} else {
		start = time.Now().UTC().Add(24 * time.Hour).Truncate(time.Hour)
	}
	var end time.Time
	if endFlag != "" {
		var err error
		end, err = time.Parse(time.RFC3339, endFlag)
		exitOnError("parse -end", err)
	} else {
		end = start.Add(time.Hour)
	}
	if !start.Before(end) {
		fmt.Println("-start must be before -end")
		os.Exit(1)
	}
	return start, end
}

func createID(client *http.Client, url string, body map[string]string) (int64, error) {
	payload, err := json.Marshal(body)
	if err != nil {
		return 0, err
	}
	resp, err := client.Post(url, "application/json", bytes.NewReader(payload))
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return 0, err
	}
	if resp.StatusCode != http.StatusCreated {
		return 0, fmt.Errorf("%s returned %d: %s", url, resp.StatusCode, respBody)
	}
	var parsed idOnly
	if err := json.Unmarshal(respBody, &parsed); err != nil {
		return 0, fmt.Errorf("%s: cannot parse response %s: %w", url, respBody, err)
	}
	if parsed.ID == 0 {
		return 0, fmt.Errorf("%s returned no id: %s", url, respBody)
	}
	return parsed.ID, nil
}

func race(client *http.Client, racers int, url string, payload []byte) []raceResult {
	results := make([]raceResult, racers)
	start := make(chan struct{})
	var wg sync.WaitGroup
	wg.Add(racers)
	for i := 0; i < racers; i++ {
		go func(slot int) {
			defer wg.Done()
			<-start
			begin := time.Now()
			resp, err := client.Post(url, "application/json", bytes.NewReader(payload))
			results[slot] = raceResult{duration: time.Since(begin)}
			if err != nil {
				results[slot].err = err
				return
			}
			defer resp.Body.Close()
			_, _ = io.Copy(io.Discard, resp.Body)
			results[slot].code = resp.StatusCode
		}(i)
	}
	close(start)
	wg.Wait()
	return results
}

func percentile(sorted []float64, p int) float64 {
	if len(sorted) == 0 {
		return 0
	}
	index := (p * len(sorted)) / 100
	if index >= len(sorted) {
		index = len(sorted) - 1
	}
	return sorted[index]
}

func exitOnError(context string, err error) {
	if err != nil {
		fmt.Printf("%s: %v\n", context, err)
		os.Exit(1)
	}
}
